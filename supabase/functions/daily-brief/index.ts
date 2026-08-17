// Günlük koç brief'i: kullanıcının kendi verisine bakıp bugün ne yapması gerektiğini söyler.
//
// Maliyet kontrolü iki katmanlı:
//   1. Günde bir brief üretilir ve `ai_briefs`'e yazılır. Aynı gün tekrar istenirse
//      AI'ya hiç gidilmez, kayıtlı brief döner.
//   2. `force` ile yenileme istenirse günlük brief limiti (varsayılan 2) uygulanır.
//
// Bağlam `coach_context()` RPC'sinden TEK sorguda geliyor; fotoğraf/isim gibi
// kişisel alan AI'ya gönderilmiyor — sadece sayısal özet.

import { createClient } from "jsr:@supabase/supabase-js@2";

const DEFAULT_BASE_URL = "https://ai.hackclub.com/proxy/v1";
const DEFAULT_MODEL = "google/gemini-2.5-flash";
const DEFAULT_DAILY_LIMIT = 2;
const DEFAULT_PREMIUM_DAILY_LIMIT = 10;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

interface Action {
  title: string;
  why: string;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

function systemPrompt(locale: string): string {
  const tr = locale.startsWith("tr");
  const language = tr ? "Turkish" : "English";
  return [
    "You are a fitness and nutrition coach inside a mobile app. You are given a JSON",
    "summary of one user's own logged data. Write today's short coaching brief.",
    "",
    `Write ALL user-facing text in ${language}, second person, warm but direct.`,
    "",
    "Answer with STRICT JSON only:",
    '{"headline":string,"focus":string,"actions":[{"title":string,"why":string}],',
    '"progress_note":string|null}',
    "",
    "Rules:",
    "- headline: max 6 words, the single thing that matters today.",
    "- focus: one or two sentences explaining today's priority, grounded in the numbers.",
    "- actions: 2 or 3 items. Each must be doable TODAY and concrete",
    "  (a number, a muscle group, a meal). No vague advice like 'eat healthy'.",
    "- why: one short sentence tying the action to the user's own data.",
    "- progress_note: what changed versus the last days, or null if there is not enough data.",
    "- If previous_brief exists, do NOT repeat the same actions; build on them.",
    "- If the user logged almost nothing, the priority is getting one small habit logged,",
    "  not a complex plan.",
    "- Beginners get simpler, fewer instructions. Advanced users get specifics.",
    "- Never diagnose, never mention medication or supplements, never promise",
    "  a rate of weight change. This is wellness guidance, not medical advice.",
    "- Do not invent data that is not in the JSON.",
  ].join("\n");
}

function parseModelJson(content: string): unknown {
  const cleaned = content.trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
  try {
    return JSON.parse(cleaned);
  } catch {
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start === -1 || end <= start) throw new Error("model JSON döndürmedi");
    return JSON.parse(cleaned.slice(start, end + 1));
  }
}

/** Düşünen modellerde content boş gelip metin reasoning alanına düşebiliyor. */
function extractContent(message: Record<string, unknown> | undefined): string {
  if (!message) return "";
  const content = message.content;
  if (typeof content === "string" && content.trim()) return content;
  if (Array.isArray(content)) {
    const joined = content
      .map((part) =>
        typeof part === "string"
          ? part
          : typeof (part as Record<string, unknown>)?.text === "string"
          ? (part as Record<string, string>).text
          : ""
      )
      .join("");
    if (joined.trim()) return joined;
  }
  const reasoning = message.reasoning_content ?? message.reasoning;
  return typeof reasoning === "string" ? reasoning : "";
}

function sanitizeActions(raw: unknown): Action[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .flatMap((entry): Action[] => {
      if (typeof entry !== "object" || entry === null) return [];
      const e = entry as Record<string, unknown>;
      const title = typeof e.title === "string" ? e.title.trim().slice(0, 120) : "";
      if (!title) return [];
      return [{
        title,
        why: typeof e.why === "string" ? e.why.trim().slice(0, 240) : "",
      }];
    })
    .slice(0, 3); // Model dörtten fazla verirse arayüz taşmasın.
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const apiKey = Deno.env.get("AI_API_KEY");
  if (!apiKey) return json({ error: "server_misconfigured" }, 500);

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "unauthorized" }, 401);

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );

  const { data: userData, error: userError } = await supabase.auth.getUser();
  const user = userData?.user;
  if (userError || !user) return json({ error: "unauthorized" }, 401);

  let body: { force?: boolean; locale?: string } = {};
  try {
    body = await req.json();
  } catch {
    // Gövde opsiyonel.
  }
  const locale = body.locale ?? "tr";
  const today = new Date().toISOString().slice(0, 10);

  // 1. Bugünün brief'i varsa AI'ya hiç gitmeyelim.
  const { data: existing } = await supabase
    .from("ai_briefs")
    .select("headline, focus, actions, progress_note, model, brief_date")
    .eq("brief_date", today)
    .maybeSingle();

  if (existing && !body.force) {
    return json({ ...existing, cached: true });
  }

  // 2. Yenileme isteniyorsa günlük limit.
  const { data: premium } = await supabase.rpc("is_premium");
  const dailyLimit = premium === true
    ? Number(Deno.env.get("PREMIUM_DAILY_BRIEFS") ?? DEFAULT_PREMIUM_DAILY_LIMIT)
    : Number(Deno.env.get("FREE_DAILY_BRIEFS") ?? DEFAULT_DAILY_LIMIT);
  const { count } = await supabase
    .from("ai_usage")
    .select("id", { count: "exact", head: true })
    .eq("kind", "daily_brief")
    .eq("usage_date", today);

  if ((count ?? 0) >= dailyLimit) {
    if (existing) return json({ ...existing, cached: true, limitReached: true });
    return json({ error: "daily_limit_reached", limit: dailyLimit }, 429);
  }

  const { data: context, error: contextError } = await supabase.rpc("coach_context");
  if (contextError) {
    console.error("coach_context_failed", contextError.message);
    return json({ error: "context_failed" }, 500);
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
        temperature: 0.4,
        max_tokens: 2500,
        ...(jsonMode ? { response_format: { type: "json_object" } } : {}),
        messages: [
          { role: "system", content: systemPrompt(locale) },
          { role: "user", content: JSON.stringify(context) },
        ],
      }),
    });

  let aiResponse: Response;
  try {
    aiResponse = await callAi(true);
    if (aiResponse.status === 400) aiResponse = await callAi(false);
  } catch (_e) {
    return json({ error: "ai_unreachable" }, 502);
  }

  if (aiResponse.status === 429) return json({ error: "ai_quota_exhausted" }, 503);
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
    return json({
      error: "unparseable_response",
      detail: {
        finishReason: choice?.finish_reason ?? null,
        contentPreview: content.slice(0, 200),
      },
    }, 502);
  }

  const headline = typeof parsed.headline === "string" ? parsed.headline.trim().slice(0, 80) : "";
  const focus = typeof parsed.focus === "string" ? parsed.focus.trim().slice(0, 600) : "";
  const actions = sanitizeActions(parsed.actions);
  const progressNote = typeof parsed.progress_note === "string"
    ? parsed.progress_note.trim().slice(0, 400)
    : null;

  if (!headline || !focus || actions.length === 0) {
    return json({ error: "incomplete_response" }, 502);
  }

  const brief = {
    user_id: user.id,
    brief_date: today,
    headline,
    focus,
    actions,
    progress_note: progressNote,
    model,
  };

  // Aynı gün için ikinci üretim üzerine yazar (unique user_id + brief_date).
  const { error: upsertError } = await supabase
    .from("ai_briefs")
    .upsert(brief, { onConflict: "user_id,brief_date" });
  if (upsertError) console.error("brief_upsert_failed", upsertError.message);

  const usage = payload?.usage ?? {};
  const { error: usageError } = await supabase.rpc("record_ai_usage", {
    p_kind: "daily_brief",
    p_model: model,
    p_tokens_in: usage.prompt_tokens ?? null,
    p_tokens_out: usage.completion_tokens ?? null,
  });
  if (usageError) console.error("usage_record_failed", usageError.message);

  return json({
    headline,
    focus,
    actions,
    progress_note: progressNote,
    model,
    brief_date: today,
    cached: false,
  });
});
