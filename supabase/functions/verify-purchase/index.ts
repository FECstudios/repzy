// Google Play satın almasını doğrular ve premium yetkisini AÇAR.
//
// Akış:
//   uygulama purchaseToken'ı gönderir
//     → servis hesabıyla Play Developer API'sine sorulur
//     → dönen duruma göre `subscriptions` satırı service_role ile yazılır
//
// NEDEN SUNUCUDA: istemcinin "premium'um" demesine güvenilmiyor. `subscriptions`
// tablosunda kullanıcı için insert/update politikası yok (migration 0009);
// satırı yalnızca burası yazabiliyor. AI çağrıları gerçek para tuttuğu için
// yetkiyi APK'ya bırakmak bedava premium demekti.
//
// GEREKEN SECRET'LAR:
//   GOOGLE_SERVICE_ACCOUNT_JSON  Play Console'a bağlı servis hesabının JSON anahtarı
//   ANDROID_PACKAGE_NAME         varsayılan com.repzy.app
//
// Servis hesabı kurulumu (tek seferlik):
//   1. Google Cloud Console → IAM → Service Accounts → yeni hesap, JSON anahtar indir
//   2. Play Console → Setup → API access → o servis hesabını bağla
//   3. Yetki: "View financial data" + "Manage orders and subscriptions"
//   Yetkiler yayılana kadar birkaç saat sürebiliyor; o süre içinde 401 döner.

import { createClient } from "jsr:@supabase/supabase-js@2";

const DEFAULT_PACKAGE = "com.repzy.app";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "content-type": "application/json" },
  });
}

// --- Servis hesabı → OAuth erişim token'ı -----------------------------------

function base64Url(input: ArrayBuffer | string): string {
  const bytes = typeof input === "string"
    ? new TextEncoder().encode(input)
    : new Uint8Array(input);
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** PEM (PKCS#8) → Web Crypto anahtarı. Servis hesabı JSON'undaki private_key bu biçimde. */
async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const raw = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    raw.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/**
 * Servis hesabı JWT'si üretip Google'dan erişim token'ı alır.
 * Token bir saat geçerli; fonksiyon örneği sıcak kaldığı sürece yeniden kullanılıyor.
 */
let cachedToken: { value: string; expiresAt: number } | null = null;

async function getAccessToken(serviceAccountJson: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt > now + 60) return cachedToken.value;

  const account = JSON.parse(serviceAccountJson) as {
    client_email: string;
    private_key: string;
  };

  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: account.client_email,
    scope: PLAY_SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600,
  }));

  const key = await importPrivateKey(account.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(`${header}.${claims}`),
  );
  const assertion = `${header}.${claims}.${base64Url(signature)}`;

  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  if (!response.ok) {
    throw new Error(`token_exchange_failed:${response.status}`);
  }

  const data = await response.json() as { access_token: string; expires_in: number };
  cachedToken = { value: data.access_token, expiresAt: now + data.expires_in };
  return data.access_token;
}

// --- Play durumu → bizim enum ------------------------------------------------

/**
 * `subscriptions.status` check kısıtı: active | in_trial | grace | expired | canceled
 *
 * CANCELED bilerek erişim veren tarafta: Play'de "iptal" otomatik yenilemenin
 * kapatılması demek, kullanıcı ödediği dönemin sonuna kadar premium kalır.
 * Süre kontrolünü expires_at yapıyor (migration 0010).
 *
 * ON_HOLD / PAUSED / PENDING erişim vermez: ödeme alınamamış ya da beklemede.
 */
function mapState(state: string | undefined): string {
  switch (state) {
    case "SUBSCRIPTION_STATE_ACTIVE":
      return "active";
    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
      return "grace";
    case "SUBSCRIPTION_STATE_CANCELED":
      return "canceled";
    case "SUBSCRIPTION_STATE_EXPIRED":
      return "expired";
    // Ödeme sorunu, duraklatma ve tamamlanmamış satın alma — erişim yok.
    case "SUBSCRIPTION_STATE_ON_HOLD":
    case "SUBSCRIPTION_STATE_PAUSED":
    case "SUBSCRIPTION_STATE_PENDING":
      return "expired";
    default:
      return "expired";
  }
}

interface PlayLineItem {
  productId?: string;
  expiryTime?: string;
  offerDetails?: { basePlanId?: string; offerId?: string };
}

interface PlaySubscription {
  subscriptionState?: string;
  startTime?: string;
  lineItems?: PlayLineItem[];
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  // Kimlik önce: yapılandırma kontrolünü öne alırsak kimliği doğrulanmamış biri
  // sunucunun kurulu olup olmadığını yoklayabiliyordu.
  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ error: "unauthorized" }, 401);

  const serviceAccountJson = Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!serviceAccountJson || !serviceRoleKey) {
    // Doğrulama kurulmadan kimseye premium verilmiyor — bilinçli "fail closed".
    return json({ error: "server_misconfigured" }, 500);
  }

  // Kullanıcıyı kendi JWT'siyle çözüyoruz; yazma işini service_role yapıyor.
  const asUser = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data: userData, error: userError } = await asUser.auth.getUser();
  const user = userData?.user;
  if (userError || !user) return json({ error: "unauthorized" }, 401);

  let body: { purchaseToken?: string; productId?: string };
  try {
    body = await req.json();
  } catch {
    return json({ error: "bad_request" }, 400);
  }

  const purchaseToken = body.purchaseToken?.trim();
  if (!purchaseToken) return json({ error: "missing_token" }, 400);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, serviceRoleKey);

  // Aynı fiş başka bir hesapta kullanılmış mı? Play token'ın hangi Supabase
  // kullanıcısına ait olduğunu bilmiyor, o yüzden bağı biz koruyoruz.
  const { data: existing } = await admin
    .from("subscriptions")
    .select("user_id")
    .eq("purchase_token", purchaseToken)
    .maybeSingle();

  if (existing && existing.user_id !== user.id) {
    return json({ error: "token_belongs_to_another_account" }, 409);
  }

  // --- Play'e sor ---
  const packageName = Deno.env.get("ANDROID_PACKAGE_NAME") ?? DEFAULT_PACKAGE;
  let purchase: PlaySubscription;
  try {
    const accessToken = await getAccessToken(serviceAccountJson);
    const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
      `${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/` +
      `${encodeURIComponent(purchaseToken)}`;

    const response = await fetch(url, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    if (response.status === 404 || response.status === 410) {
      // Play böyle bir token tanımıyor: sahte ya da çok eski.
      return json({ error: "purchase_not_found" }, 404);
    }
    if (!response.ok) {
      const detail = await response.text();
      console.error("play_api_failed", response.status, detail.slice(0, 500));
      return json({ error: "play_api_failed" }, 502);
    }
    purchase = await response.json() as PlaySubscription;
  } catch (e) {
    console.error("verify_failed", e instanceof Error ? e.message : e);
    return json({ error: "verify_failed" }, 502);
  }

  const status = mapState(purchase.subscriptionState);
  const line = purchase.lineItems?.[0];
  const expiresAt = line?.expiryTime ?? null;

  const { error: writeError } = await admin
    .from("subscriptions")
    .upsert({
      user_id: user.id,
      status,
      product_id: line?.productId ?? body.productId ?? null,
      purchase_token: purchaseToken,
      started_at: purchase.startTime ?? new Date().toISOString(),
      expires_at: expiresAt,
      updated_at: new Date().toISOString(),
    }, { onConflict: "user_id" });

  if (writeError) {
    console.error("subscription_write_failed", writeError.message);
    return json({ error: "write_failed" }, 500);
  }

  // İstemci bunu göstermek zorunda değil; yetkiyi yine `is_premium()` söylüyor.
  const premium = ["active", "in_trial", "grace", "canceled"].includes(status) &&
    (!expiresAt || new Date(expiresAt) > new Date());

  return json({ status, premium, expiresAt });
});
