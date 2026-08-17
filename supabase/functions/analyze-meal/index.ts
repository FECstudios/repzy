// Fotoğraftan kalori/makro tahmini.
//
// API key SADECE burada durur — Android tarafına asla konmaz (APK decompile edilir).
// Sağlayıcı değişirse sadece AI_BASE_URL + AI_MODEL secret'ları değişir, uygulama güncellenmez.
//
// Secret'lar:
//   AI_API_KEY   (zorunlu)  Hack Club AI ya da OpenAI uyumlu sağlayıcı key'i
//   AI_BASE_URL  (opsiyonel) varsayılan https://ai.hackclub.com/proxy/v1
//   AI_MODEL     (opsiyonel) varsayılan google/gemini-2.5-flash
//   FREE_DAILY_FOOD_SCANS (opsiyonel) varsayılan 5

import { createClient } from "jsr:@supabase/supabase-js@2";

const DEFAULT_BASE_URL = "https://ai.hackclub.com/proxy/v1";
const DEFAULT_MODEL = "google/gemini-2.5-flash";
const DEFAULT_DAILY_LIMIT = 5;

// Base64 payload üst sınırı. İstemci ~1024 px JPEG gönderiyor (~300 KB);
// 8 MB'lık ham fotoğraf hem yavaş hem pahalı, kapıda kesiyoruz.
const MAX_IMAGE_BYTES = 1_500_000;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface MealItem {
  name: string;
  grams: number | null;
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  fiber_g: number | null;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

function promptFor(locale: string): string {
  const tr = locale.startsWith("tr");
  const language = tr ? "Türkçe" : "English";
  return [
    "You are a nutrition estimation engine. Analyse the meal photo and estimate",
    "the portion size and macros for each distinct food item you can identify.",
    `Write every "name" value in ${language}.`,
    "",
    "Answer with STRICT JSON only — no markdown fences, no commentary:",
    '{"items":[{"name":string,"grams":number|null,"calories":number,',
    '"protein_g":number,"carbs_g":number,"fat_g":number,"fiber_g":number|null}],',
    '"confidence":number,"note":string|null}',
    "",
    "Rules:",
    "- calories/macros are for the portion visible, not per 100 g.",
    "- confidence is 0..1: how sure you are about the identification AND the portion.",
    "  Plated home food with no size reference is rarely above 0.7.",
    "- If the photo contains no food, return an empty items array and explain in note.",
    "- Never invent a brand or an exact recipe you cannot see.",
    `- note is a short sentence in ${language}, or null.`,
  ].join("\n");
}

/**
 * Cevap metnini çıkarır. OpenAI uyumlu sağlayıcılar üç farklı şekil döndürebiliyor:
 * düz metin, içerik parçaları dizisi, ya da (düşünen modellerde) content boş +
 * metin reasoning alanında.
 */
function extractContent(message: Record<string, unknown> | undefined): string {
  if (!message) return "";
  const content = message.content;
  if (typeof content === "string" && content.trim()) return content;
  if (Array.isArray(content)) {
    const joined = content
      .map((part) => {
        if (typeof part === "string") return part;
        if (typeof part === "object" && part !== null) {
          const text = (part as Record<string, unknown>).text;
          return typeof text === "string" ? text : "";
        }
        return "";
      })
      .join("");
    if (joined.trim()) return joined;
  }
  const reasoning = message.reasoning_content ?? message.reasoning;
  return typeof reasoning === "string" ? reasoning : "";
}

/** Modelin döndürdüğü metinden JSON'u çıkarır — bazı modeller ``` ile sarıyor. */
function parseModelJson(content: string): unknown {
  const cleaned = content.trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
  try {
    return JSON.parse(cleaned);
  } catch {
    // İlk { ile son } arasını dene: model bazen önüne bir cümle ekliyor.
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start === -1 || end <= start) throw new Error("model JSON döndürmedi");
    return JSON.parse(cleaned.slice(start, end + 1));
  }
}

function num(value: unknown, fallback: number | null = null): number | null {
  const n = typeof value === "string" ? Number(value) : value;
  if (typeof n !== "number" || !Number.isFinite(n) || n < 0) return fallback;
  return Math.round(n * 10) / 10;
}

/** Modelin çıktısı DB kısıtlarına uymak zorunda — negatif/saçma değerleri buradan geçirmeyiz. */
function sanitizeItems(raw: unknown): MealItem[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((entry): MealItem[] => {
    if (typeof entry !== "object" || entry === null) return [];
    const e = entry as Record<string, unknown>;
    const name = typeof e.name === "string" ? e.name.trim().slice(0, 120) : "";
    const calories = num(e.calories);
    if (!name || calories === null) return [];
    return [{
      name,
      grams: num(e.grams),
      calories,
      protein_g: num(e.protein_g, 0)!,
      carbs_g: num(e.carbs_g, 0)!,
      fat_g: num(e.fat_g, 0)!,
      fiber_g: num(e.fiber_g),
    }];
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const apiKey = Deno.env.get("AI_API_KEY");
  if (!apiKey) return json({ error: "server_misconfigured" }, 500);

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "unauthorized" }, 401);

  // Kullanıcının kendi JWT'siyle çalışıyoruz: RLS açık kalıyor, service key'e hiç gerek yok.
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );

  const { data: userData, error: userError } = await supabase.auth.getUser();
  const user = userData?.user;
  if (userError || !user) return json({ error: "unauthorized" }, 401);

  let body: { image?: string; mimeType?: string; locale?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }

  const image = body.image?.replace(/^data:[^;]+;base64,/, "");
  if (!image) return json({ error: "image_required" }, 400);
  if (image.length > MAX_IMAGE_BYTES) return json({ error: "image_too_large" }, 413);

  const mimeType = body.mimeType === "image/png" ? "image/png" : "image/jpeg";
  const locale = body.locale ?? "tr";

  // Ücretsiz katman taraması: günlük limit. Vision çağrısı maliyetin tamamı burada.
  const dailyLimit = Number(Deno.env.get("FREE_DAILY_FOOD_SCANS") ?? DEFAULT_DAILY_LIMIT);
  const today = new Date().toISOString().slice(0, 10);
  const { count, error: countError } = await supabase
    .from("ai_usage")
    .select("id", { count: "exact", head: true })
    .eq("kind", "food_photo")
    .eq("usage_date", today);

  if (countError) return json({ error: "usage_check_failed" }, 500);
  const used = count ?? 0;
  if (used >= dailyLimit) {
    return json({ error: "daily_limit_reached", limit: dailyLimit, used }, 429);
  }

  const model = Deno.env.get("AI_MODEL") ?? DEFAULT_MODEL;
  const baseUrl = Deno.env.get("AI_BASE_URL") ?? DEFAULT_BASE_URL;

  const callAi = (jsonMode: boolean) =>
    fetch(`${baseUrl}/chat/completions`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model,
        temperature: 0.2,
        // Düşünen modeller (gemini-2.5-flash gibi) bütçenin bir kısmını akıl yürütmeye
        // harcıyor; 900 token'da içerik boş dönüyordu.
        max_tokens: 2500,
        ...(jsonMode ? { response_format: { type: "json_object" } } : {}),
        messages: [
          { role: "system", content: promptFor(locale) },
          {
            role: "user",
            content: [
              { type: "text", text: "Analyse this meal." },
              {
                type: "image_url",
                image_url: { url: `data:${mimeType};base64,${image}` },
              },
            ],
          },
        ],
      }),
    });

  let aiResponse: Response;
  try {
    aiResponse = await callAi(true)
    // json modu her modelde yok; 400 dönerse aynı isteği onsuz tekrar dene.
    if (aiResponse.status === 400) {
      console.warn("json_mode_rejected, retrying without response_format");
      aiResponse = await callAi(false);
    }
  } catch (_e) {
    return json({ error: "ai_unreachable" }, 502);
  }

  if (aiResponse.status === 429) {
    // Sağlayıcının kendi kotası doldu — kullanıcının limitiyle karıştırmayalım.
    return json({ error: "ai_quota_exhausted" }, 503);
  }
  if (!aiResponse.ok) {
    console.error("ai_error", aiResponse.status, await aiResponse.text());
    return json({ error: "ai_failed" }, 502);
  }

  const payload = await aiResponse.json();
  const choice = payload?.choices?.[0];
  const content = extractContent(choice?.message);

  let parsed: Record<string, unknown>;
  try {
    parsed = parseModelJson(content) as Record<string, unknown>;
  } catch (_e) {
    console.error("parse_failed", JSON.stringify(choice)?.slice(0, 800));
    // detail teşhis için: modelin ne döndürdüğü log'a bakmadan görülebilsin.
    return json({
      error: "unparseable_response",
      detail: {
        finishReason: choice?.finish_reason ?? null,
        contentPreview: content.slice(0, 200),
        usage: payload?.usage ?? null,
      },
    }, 502);
  }

  const items = sanitizeItems(parsed.items);
  const confidence = Math.min(1, Math.max(0, num(parsed.confidence, 0.5)!));
  const note = typeof parsed.note === "string" ? parsed.note.slice(0, 300) : null;

  // Kullanım her başarılı çağrıda yazılır — yemek bulunamasa da vision maliyeti oluştu.
  // RPC üzerinden: ai_usage'a kullanıcı token'ıyla doğrudan insert RLS'e takılıyor.
  const usage = payload?.usage ?? {};
  const { error: usageError } = await supabase.rpc("record_ai_usage", {
    p_kind: "food_photo",
    p_model: model,
    p_tokens_in: usage.prompt_tokens ?? null,
    p_tokens_out: usage.completion_tokens ?? null,
  });
  // Kaydedilemezse limit uygulanamaz demektir — sessizce geçmeyelim, log'da bağıralım.
  if (usageError) console.error("usage_record_failed", usageError.message);

  const total = items.reduce(
    (acc, item) => ({
      calories: acc.calories + item.calories,
      protein_g: Math.round((acc.protein_g + item.protein_g) * 10) / 10,
      carbs_g: Math.round((acc.carbs_g + item.carbs_g) * 10) / 10,
      fat_g: Math.round((acc.fat_g + item.fat_g) * 10) / 10,
    }),
    { calories: 0, protein_g: 0, carbs_g: 0, fat_g: 0 },
  );

  return json({
    items,
    total,
    confidence,
    note,
    model,
    scansRemaining: Math.max(0, dailyLimit - used - 1),
  });
});
