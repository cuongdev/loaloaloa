/**
 * Loa Loa Loa relay Sender — a Cloudflare Worker that fans a shop's hub transactions out to its
 * employee "spoke" phones via Firebase Cloud Messaging (FCM HTTP v1).
 *
 * It is a blind relay: the transaction payload is AES-256-GCM encrypted end-to-end on the hub
 * (see app `RelayCrypto`), so this Worker and Google only ever move ciphertext (the `blob`). The
 * Worker authenticates requests with HMAC-SHA256 over the raw request body, using each room's MAC
 * subkey — which is independent of the encryption key, so holding it never lets the Worker decrypt.
 *
 * State lives in one KV namespace (binding `RELAY_KV`), one entry per room:
 *     room:{roomId} -> { "macKey": "<base64url>",
 *                        "devices": [{ "token": "<fcm token>", "label": "<device name>", "ts": <epoch s> }, ...] }
 * (Rooms written by an older build as `{ macKey, tokens: [...] }` are migrated on read — see
 *  normalizeRoom — so the upgrade needs no data migration step.)
 *
 * The encrypted transaction history the web dashboard reads back lives in D1 (binding `RELAY_DB`),
 * one row per transaction: { room, hash = SHA-256(blob), blob (ciphertext), ts (server receipt ms) }.
 * The relay never holds the room secret, so these rows are opaque to it — the dashboard decrypts them.
 *
 * Endpoints (all POST, body is JSON, signature in the `X-Relay-Signature` header):
 *   POST /register  {roomId, macKey, token?, label?} — hub provisions the room's macKey (trust-on-
 *                                                first-use, pinned); spoke upserts its FCM token with an
 *                                                optional human label (device name). Signed with macKey.
 *   POST /send      {roomId, blob}             — hub relays one encrypted transaction; fanned out to
 *                                                all of the room's device tokens as a high-priority FCM
 *                                                data message with a short TTL (no stale replay). The
 *                                                ciphertext is also logged to D1 (binding RELAY_DB) for
 *                                                the web dashboard — see persistHistory; E2E-blind.
 *   POST /history   {roomId, before?, limit?}  — a paired peer (the web dashboard) pulls a page of the
 *                                                room's stored ciphertext rows ({blob, ts}), newest
 *                                                first, to decrypt + rebuild history/reports on-device.
 *   POST /devices   {roomId}                   — list the room's paired spoke devices ([{token, label,
 *                                                ts}]) plus the pair/unpair audit log (events[]), so a
 *                                                hub or web peer can show + manage them.
 *   POST /revoke    {roomId, token}            — drop one spoke device (by token) from the room (logged).
 *   GET  /                                     — health check.
 *   GET  /pair                                 — HTML pairing landing page; an employee who scans the
 *                                                pairing QR without the app lands here to install it.
 *                                                The pairing token rides in the URL fragment (#…), so
 *                                                this Worker never sees it.
 *   GET  /download                             — 302 to the APK (env `APK_URL`), for that landing page.
 *   GET  /.well-known/assetlinks.json          — Digital Asset Links; lets Android verify this host so
 *                                                the /pair link opens the app instead of the browser.
 *   GET  /web (+ /web/app.js, /web/config.js,   — the web spoke + dashboard PWA: hear a room's
 *        /web/manifest.webmanifest,               transactions AND see its full history + reports in a
 *        /firebase-messaging-sw.js)               browser, no app. A full spoke peer (registers an FCM
 *                                                 web token, decrypts the same blob). See spoke.js.
 *
 * Required Worker secret (never in the repo / APK): `FCM_SERVICE_ACCOUNT` = the full Firebase
 * service-account JSON. Optional vars: `APK_URL` = where to download the signed APK (for /download);
 * `FIREBASE_WEB_CONFIG` (JSON) + `VAPID_PUBLIC_KEY` = the Firebase *web* app config + Web Push key the
 * /web PWA needs (both public, not secrets). See README.md for setup + deploy.
 */

import { spokeIndexHtml, spokeAppJs, spokeConfigJs, spokeSwJs, spokeManifest } from "./spoke.js";
import { landingPage } from "./landing.js";

// Cached Google OAuth access token, reused across requests within a Worker isolate until it nears
// expiry. Minting it costs an RSA sign + a round-trip, so caching keeps /send fast under burst.
let cachedAccessToken = null; // { token: string, exp: number(epoch seconds) }

const FCM_TTL_SECONDS = 180; // Short: a push that can't land in a few minutes expires, never spoken stale.

// How long an encrypted transaction lingers in D1 for the web dashboard. Must comfortably exceed the
// app's six-month report window so /history can rebuild every chart; older rows are pruned on write.
const HISTORY_RETENTION_DAYS = 210;
const HISTORY_PAGE_MAX = 1000; // hard cap on rows one /history page returns
const HISTORY_PAGE_DEFAULT = 500;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET") {
      switch (url.pathname) {
        case "/":
          // Marketing landing page (see landing.js). Health checks moved to /healthz.
          return html(landingPage());
        case "/healthz":
          return text("loaloaloa relay sender: ok");
        case "/pair":
          // Pairing landing page. The token rides in the URL fragment, so it never reaches us here.
          return pairLandingPage();
        case "/download":
          return downloadApk(env);
        case "/.well-known/assetlinks.json":
          // Digital Asset Links — lets Android verify this host so the /pair App Link opens the app.
          return assetLinks();
        // Web spoke + dashboard PWA (hear transactions + see history/reports in a browser). See spoke.js.
        case "/web":
        case "/web/":
          return html(spokeIndexHtml());
        case "/web/app.js":
          return js(spokeAppJs());
        case "/web/config.js":
          return js(spokeConfigJs(env));
        // Root-scoped so it controls the /web page (scope '/'); FCM needs the SW to forward
        // foreground messages to the open tab. No fetch handler, so it doesn't affect other pages.
        case "/firebase-messaging-sw.js":
          return js(spokeSwJs(env));
        case "/web/manifest.webmanifest":
          return new Response(spokeManifest(), {
            headers: { "Content-Type": "application/manifest+json; charset=utf-8" },
          });
        default:
          return json({ error: "not found" }, 404);
      }
    }
    if (request.method !== "POST") {
      return json({ error: "method not allowed" }, 405);
    }

    try {
      switch (url.pathname) {
        case "/register":
          return await handleRegister(request, env);
        case "/send":
          return await handleSend(request, env);
        case "/history":
          return await handleHistory(request, env);
        case "/devices":
          return await handleDevices(request, env);
        case "/revoke":
          return await handleRevoke(request, env);
        case "/close":
          return await handleClose(request, env);
        default:
          return json({ error: "not found" }, 404);
      }
    } catch (e) {
      console.error("relay error", e);
      return json({ error: "internal", detail: String(e && e.message ? e.message : e) }, 500);
    }
  },
};

/**
 * Provision a room (hub) and/or register a spoke's FCM token. The room's macKey is pinned on first
 * contact (trust-on-first-use); a later request presenting a different macKey for the same room is
 * rejected, and every request must be HMAC-signed with the room's macKey.
 */
async function handleRegister(request, env) {
  const bodyText = await request.text();
  const signature = request.headers.get("X-Relay-Signature") || "";
  const body = parseJson(bodyText);
  if (!body) return json({ error: "bad json" }, 400);

  const { roomId, macKey, token, label } = body;
  if (!isNonBlank(roomId) || !isNonBlank(macKey)) {
    return json({ error: "roomId and macKey required" }, 400);
  }

  const key = roomKey(roomId);
  const existing = normalizeRoom(await env.RELAY_KV.get(key, "json"));
  if (existing && existing.macKey !== macKey) {
    // The roomId is already pinned to a different secret — refuse to take it over.
    return json({ error: "room already registered with a different key" }, 409);
  }

  // First contact verifies against the presented macKey (self-certifying); later against the pinned.
  const pinnedMac = existing ? existing.macKey : macKey;
  if (!(await verifySignature(pinnedMac, bodyText, signature))) {
    return json({ error: "bad signature" }, 401);
  }

  const room = existing || { macKey, devices: [] };
  let added = false;
  if (isNonBlank(token)) {
    const device = room.devices.find((d) => d.token === token);
    if (device) {
      // Re-register (re-pair or token reuse): refresh the label + last-seen, don't duplicate.
      if (isNonBlank(label)) device.label = label;
      device.ts = nowSeconds();
    } else {
      const devLabel = isNonBlank(label) ? label : "";
      room.devices.push({ token, label: devLabel, ts: nowSeconds() });
      pushEvent(room, "pair", token, devLabel);
      added = true;
    }
  }
  await env.RELAY_KV.put(key, JSON.stringify(room));
  return json({ ok: true, devices: room.devices.length, added });
}

/**
 * Fan one encrypted transaction out to every spoke token in the room. Dead tokens (FCM 404/400) are
 * pruned; if any delivery hit a transient/auth error the whole call returns 503 so the hub's
 * WorkManager retries (spokes dedupe the repeat by txId, so re-delivery is safe).
 */
async function handleSend(request, env) {
  const bodyText = await request.text();
  const signature = request.headers.get("X-Relay-Signature") || "";
  const body = parseJson(bodyText);
  if (!body) return json({ error: "bad json" }, 400);

  const { roomId, blob } = body;
  if (!isNonBlank(roomId) || !isNonBlank(blob)) {
    return json({ error: "roomId and blob required" }, 400);
  }

  const key = roomKey(roomId);
  const room = normalizeRoom(await env.RELAY_KV.get(key, "json"));
  if (!room) return json({ error: "unknown room" }, 404);
  if (!(await verifySignature(room.macKey, bodyText, signature))) {
    return json({ error: "bad signature" }, 401);
  }

  // Persist the ciphertext for the web dashboard (Approach B) BEFORE the no-devices early return, so
  // history syncs even for rooms with only web spokes (or none yet). E2E-blind: only blob + hash + ts.
  // A hub WorkManager retry resends the identical blob → same hash → INSERT OR IGNORE is a no-op.
  await persistHistory(env, roomId, blob);

  if (room.devices.length === 0) {
    return json({ ok: true, delivered: 0, pruned: 0 });
  }

  const serviceAccount = JSON.parse(env.FCM_SERVICE_ACCOUNT);
  const accessToken = await getAccessToken(serviceAccount);

  let delivered = 0;
  let transientFailures = 0;
  const dead = [];

  for (const device of room.devices) {
    const resp = await sendFcm(serviceAccount.project_id, accessToken, device.token, blob);
    if (resp.ok) {
      delivered++;
    } else if (resp.status === 404 || resp.status === 400) {
      // UNREGISTERED (app uninstalled / token expired) or invalid token → drop it.
      dead.push(device.token);
    } else {
      // 401/403/429/5xx — transient or auth; keep the token and let the hub retry the send.
      transientFailures++;
      console.warn("fcm send failed", resp.status, await safeText(resp));
    }
  }

  if (dead.length) {
    room.devices = room.devices.filter((d) => !dead.includes(d.token));
    await env.RELAY_KV.put(key, JSON.stringify(room));
  }

  if (transientFailures > 0) {
    return json({ ok: false, delivered, pruned: dead.length, retry: transientFailures }, 503);
  }
  return json({ ok: true, delivered, pruned: dead.length });
}

/**
 * Store one encrypted transaction in D1 for later dashboard playback, and prune anything past the
 * retention window for this room in the same batch. Best-effort: a D1 hiccup must never fail a relay
 * send (the live announce is what matters), so errors are swallowed with a warning. No-op if D1 isn't
 * bound (the relay runs fine without the dashboard).
 */
async function persistHistory(env, roomId, blob) {
  if (!env.RELAY_DB) return;
  try {
    const hash = await sha256Hex(blob);
    const now = Date.now();
    const cutoff = now - HISTORY_RETENTION_DAYS * 86400000;
    await env.RELAY_DB.batch([
      env.RELAY_DB.prepare("INSERT OR IGNORE INTO tx (room, hash, blob, ts) VALUES (?, ?, ?, ?)").bind(roomId, hash, blob, now),
      env.RELAY_DB.prepare("DELETE FROM tx WHERE room = ? AND ts < ?").bind(roomId, cutoff),
    ]);
  } catch (e) {
    console.warn("history persist failed", e && e.message ? e.message : e);
  }
}

/**
 * Return a page of a room's stored ciphertext rows, newest first, for the web dashboard to decrypt
 * and rebuild history + reports on-device. Signed with the room macKey (same auth as every endpoint),
 * so only a paired peer can pull — and even it gets only ciphertext + timestamps. Body:
 *   {roomId, before?: epoch-ms cursor (exclusive), limit?: 1..1000}
 * Response: {ok, rows:[{blob, ts}], more}. Page older history by passing the last row's `ts` as `before`.
 */
async function handleHistory(request, env) {
  const bodyText = await request.text();
  const signature = request.headers.get("X-Relay-Signature") || "";
  const body = parseJson(bodyText);
  if (!body) return json({ error: "bad json" }, 400);

  const { roomId } = body;
  if (!isNonBlank(roomId)) return json({ error: "roomId required" }, 400);

  const key = roomKey(roomId);
  const room = normalizeRoom(await env.RELAY_KV.get(key, "json"));
  if (!room) return json({ error: "unknown room" }, 404);
  if (!(await verifySignature(room.macKey, bodyText, signature))) {
    return json({ error: "bad signature" }, 401);
  }
  if (!env.RELAY_DB) return json({ ok: true, rows: [], more: false });

  let before = Number(body.before);
  if (!Number.isFinite(before) || before <= 0) before = Number.MAX_SAFE_INTEGER;
  let limit = Math.floor(Number(body.limit));
  if (!Number.isFinite(limit) || limit <= 0) limit = HISTORY_PAGE_DEFAULT;
  if (limit > HISTORY_PAGE_MAX) limit = HISTORY_PAGE_MAX;

  const res = await env.RELAY_DB.prepare(
    "SELECT blob, ts FROM tx WHERE room = ? AND ts < ? ORDER BY ts DESC LIMIT ?",
  )
    .bind(roomId, before, limit)
    .all();
  const rows = (res.results || []).map((r) => ({ blob: r.blob, ts: r.ts }));
  return json({ ok: true, rows, more: rows.length === limit });
}

/** List a room's paired spoke devices so the hub can show + manage them. Signed with the macKey. */
async function handleDevices(request, env) {
  const bodyText = await request.text();
  const signature = request.headers.get("X-Relay-Signature") || "";
  const body = parseJson(bodyText);
  if (!body) return json({ error: "bad json" }, 400);

  const { roomId } = body;
  if (!isNonBlank(roomId)) return json({ error: "roomId required" }, 400);

  const key = roomKey(roomId);
  const room = normalizeRoom(await env.RELAY_KV.get(key, "json"));
  if (!room) return json({ error: "unknown room" }, 404);
  if (!(await verifySignature(room.macKey, bodyText, signature))) {
    return json({ error: "bad signature" }, 401);
  }

  // Only the room's own hub holds the macKey, so returning the (trusted) tokens here is safe; the
  // hub needs the token to address a /revoke. Most-recent first so the newest pairing is on top.
  const devices = room.devices
    .map((d) => ({ token: d.token, label: d.label || "", ts: d.ts || 0 }))
    .sort((a, b) => b.ts - a.ts);
  // Pair/unpair audit log, newest first, so a hub or web peer can show each device's history.
  const events = (room.events || [])
    .map((e) => ({ type: e.type, token: e.token, label: e.label || "", ts: e.ts || 0 }))
    .sort((a, b) => b.ts - a.ts);
  return json({ ok: true, devices, events });
}

/** Remove one spoke device (by token) from a room (hub-initiated revoke). */
async function handleRevoke(request, env) {
  const bodyText = await request.text();
  const signature = request.headers.get("X-Relay-Signature") || "";
  const body = parseJson(bodyText);
  if (!body) return json({ error: "bad json" }, 400);

  const { roomId, token } = body;
  if (!isNonBlank(roomId) || !isNonBlank(token)) {
    return json({ error: "roomId and token required" }, 400);
  }

  const key = roomKey(roomId);
  const room = normalizeRoom(await env.RELAY_KV.get(key, "json"));
  if (!room) return json({ error: "unknown room" }, 404);
  if (!(await verifySignature(room.macKey, bodyText, signature))) {
    return json({ error: "bad signature" }, 401);
  }

  const removed = room.devices.find((d) => d.token === token);
  const before = room.devices.length;
  room.devices = room.devices.filter((d) => d.token !== token);
  if (removed) pushEvent(room, "unpair", token, removed.label);
  await env.RELAY_KV.put(key, JSON.stringify(room));
  return json({ ok: true, removed: before - room.devices.length });
}

// POST /close {roomId} — the hub destroys the whole room: delete it from KV so every spoke
// (phone or web) gets a 404 on its next /devices poll and unpairs itself. Signed with the room's
// macKey, so only a device holding the room secret can close it.
async function handleClose(request, env) {
  const bodyText = await request.text();
  const signature = request.headers.get("X-Relay-Signature") || "";
  const body = parseJson(bodyText);
  if (!body) return json({ error: "bad json" }, 400);

  const { roomId } = body;
  if (!isNonBlank(roomId)) return json({ error: "roomId required" }, 400);

  const key = roomKey(roomId);
  const room = normalizeRoom(await env.RELAY_KV.get(key, "json"));
  if (!room) return json({ ok: true, closed: false });
  if (!(await verifySignature(room.macKey, bodyText, signature))) {
    return json({ error: "bad signature" }, 401);
  }

  await env.RELAY_KV.delete(key);
  return json({ ok: true, closed: true });
}

// ---------------------------------------------------------------------------
// Pairing landing + APK install (App Links)
// ---------------------------------------------------------------------------

// The Android app this relay pairs. The fingerprint(s) must match the keystore that signs the APK
// you ship — Android only opens the /pair link in the app when one of these verifies this host.
const ANDROID_PACKAGE = "com.loaloaloa";
const ANDROID_CERT_SHA256 = [
  // Debug keystore (~/.android/debug.keystore). Add your RELEASE signing SHA-256 here before
  // distributing a signed APK, or the link won't auto-open on release builds.
  "EF:3A:DF:F3:37:F5:D0:B7:3F:C4:32:14:55:7E:97:E4:FB:70:CC:89:F9:4A:CA:DA:28:16:D2:EA:00:E6:51:85",
];

/** Digital Asset Links file proving this host belongs to the app, so Android verifies the App Link. */
function assetLinks() {
  const body = JSON.stringify([
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: ANDROID_PACKAGE,
        sha256_cert_fingerprints: ANDROID_CERT_SHA256,
      },
    },
  ]);
  return new Response(body, {
    status: 200,
    headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=3600" },
  });
}

/** Redirect to the hosted APK (env `APK_URL`); 503 with a hint if it hasn't been configured yet. */
function downloadApk(env) {
  const target = env && env.APK_URL;
  if (!target) {
    return text("APK chưa được cấu hình. Đặt biến APK_URL cho Worker (wrangler.toml hoặc secret).", 503);
  }
  return Response.redirect(target, 302);
}

/**
 * The page an employee sees when they scan the pairing QR without the app (the App Link couldn't be
 * handled, so the browser opened this URL). It offers the APK and, once installed, an "open in app"
 * button that re-navigates to the same /pair link — which then opens the app and pairs. The pairing
 * token lives in the URL fragment, read only by this page's script; it is never sent to the server.
 */
function pairLandingPage() {
  const html = `<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
<title>Ghép máy — Loa Loa Loa</title>
<style>
  :root { color-scheme: light dark; }
  * { box-sizing: border-box; }
  body { margin:0; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
         background:#0f1115; color:#e7e9ee; min-height:100vh; display:flex; align-items:center;
         justify-content:center; padding:24px; }
  .card { width:100%; max-width:420px; background:#171a21; border:1px solid #232733; border-radius:20px;
          padding:28px 24px; text-align:center; }
  .logo { font-size:48px; line-height:1; }
  h1 { font-size:22px; margin:14px 0 4px; }
  .tag { color:#9aa3b2; font-size:15px; margin:0 0 20px; }
  .btn { display:block; width:100%; padding:15px 18px; border-radius:14px; font-size:16px;
         font-weight:600; text-decoration:none; border:0; cursor:pointer; margin-top:12px; }
  .primary { background:#4c8dff; color:#fff; }
  .ghost { background:transparent; color:#9aa3b2; border:1px solid #2c313d; }
  ol { text-align:left; color:#c4cad6; font-size:14px; line-height:1.6; padding-left:20px; margin:22px 0 0; }
  .note { color:#7a8294; font-size:12px; margin-top:20px; line-height:1.5; }
  .warn { background:#2a1d14; border:1px solid #5a3a1f; color:#ffce9e; border-radius:14px;
          padding:14px; font-size:14px; margin-top:8px; }
  .hidden { display:none; }
</style>
</head>
<body>
  <div class="card">
    <div class="logo"><svg width="56" height="56" viewBox="0 0 64 64" role="img" aria-label="Loa Loa Loa"><g fill="#0EA5A4"><path d="M6 26h8v12H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z"/><path d="M14 24 42 14v36L14 40z"/><path d="M20 40h6v9a3 3 0 0 1-6 0z"/></g><path d="M48 24q6 8 0 16" fill="none" stroke="#0EA5A4" stroke-width="3.5" stroke-linecap="round"/><path d="M54 19q10 13 0 26" fill="none" stroke="#5EEAD4" stroke-width="3.5" stroke-linecap="round"/></svg></div>
    <h1>Ghép máy nhân viên</h1>
    <p class="tag">Nghe thông báo giao dịch của shop trên máy này — không cần đăng nhập ngân hàng.</p>

    <div id="missing" class="warn hidden">
      Thiếu mã ghép. Hãy quét mã QR đang hiện trên máy shop.
    </div>

    <div id="ios" class="warn hidden">
      Máy này không phải Android — không cài được ứng dụng. Hãy nhấn <b>Nghe trên trình duyệt</b> bên dưới
      (để tab mở để nghe), hoặc dùng một máy Android.
    </div>

    <div id="install" class="hidden">
      <a class="btn primary" href="/download">Tải ứng dụng (.apk)</a>
      <button class="btn ghost" id="openApp">Đã cài? Mở trong ứng dụng</button>
      <ol>
        <li>Nhấn <b>Tải ứng dụng</b> rồi cài đặt (cho phép “Cài từ nguồn này” nếu Android hỏi).</li>
        <li>Quay lại đây và nhấn <b>Mở trong ứng dụng</b> để ghép phòng — hoặc quét lại mã QR.</li>
      </ol>
    </div>

    <a id="web" class="btn ghost hidden" href="#">Nghe trên trình duyệt (web)</a>

    <p class="note">Mã ghép nằm sau dấu # trên đường liên kết và chỉ được đọc trên máy bạn — máy chủ không nhận được mã này.</p>
  </div>
<script>
  (function () {
    var token = (location.hash || "").replace(/^#/, "");
    var isIos = /iPhone|iPad|iPod/.test(navigator.userAgent) && !window.MSStream;
    if (!token) {
      document.getElementById("missing").classList.remove("hidden");
      return;
    }
    if (isIos) {
      document.getElementById("ios").classList.remove("hidden");
    } else {
      document.getElementById("install").classList.remove("hidden");
      // Re-navigate to the same /pair link (cache-busting query forces a real navigation, fragment
      // preserved) so a verified, installed app intercepts it and opens straight into pairing.
      document.getElementById("openApp").addEventListener("click", function () {
        location.href = "/pair?o=" + Date.now() + "#" + token;
      });
    }
    // Web spoke fallback (works on any browser, incl. iOS/desktop): carry the same token in the
    // fragment to /web, which never sends it to the server either.
    var web = document.getElementById("web");
    web.href = "/web#" + token;
    web.classList.remove("hidden");
  })();
</script>
</body>
</html>`;
  return new Response(html, { status: 200, headers: { "Content-Type": "text/html; charset=utf-8" } });
}

// ---------------------------------------------------------------------------
// FCM HTTP v1
// ---------------------------------------------------------------------------

/** POST one high-priority data message carrying the ciphertext `blob` to a single device token. */
function sendFcm(projectId, accessToken, token, blob) {
  return fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token,
        // data-only (no `notification`) → wakes a dozing/killed app silently, no tray notification.
        data: { blob },
        android: { priority: "HIGH", ttl: `${FCM_TTL_SECONDS}s` },
        // Web-spoke tokens (the /web PWA) get the same short TTL so an undelivered push expires
        // rather than arriving stale; ignored for native Android tokens.
        webpush: { headers: { TTL: `${FCM_TTL_SECONDS}`, Urgency: "high" } },
      },
    }),
  });
}

/** Mint (or reuse) a Google OAuth2 access token for the FCM scope from the service account. */
async function getAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedAccessToken && cachedAccessToken.exp > now + 60) {
    return cachedAccessToken.token;
  }

  const claims = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${b64urlFromBytes(utf8(JSON.stringify({ alg: "RS256", typ: "JWT" })))}.${b64urlFromBytes(utf8(JSON.stringify(claims)))}`;
  const signingKey = await importPkcs8(serviceAccount.private_key);
  const sig = await crypto.subtle.sign({ name: "RSASSA-PKCS1-v1_5" }, signingKey, utf8(unsigned));
  const jwt = `${unsigned}.${b64urlFromBytes(new Uint8Array(sig))}`;

  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  const data = await resp.json();
  if (!resp.ok) throw new Error(`oauth token failed: ${JSON.stringify(data)}`);

  cachedAccessToken = { token: data.access_token, exp: now + (data.expires_in || 3600) };
  return cachedAccessToken.token;
}

/** Import a PEM PKCS#8 RSA private key (from the service account) for RS256 signing. */
function importPkcs8(pem) {
  const der = base64ToBytes(
    pem.replace(/-----BEGIN PRIVATE KEY-----/, "").replace(/-----END PRIVATE KEY-----/, "").replace(/\s+/g, ""),
  );
  return crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

// ---------------------------------------------------------------------------
// Crypto + encoding helpers
// ---------------------------------------------------------------------------

/** Constant-time HMAC-SHA256 verify of base64url [signature] over [bodyText] under base64url [macKeyB64url]. */
async function verifySignature(macKeyB64url, bodyText, signatureB64url) {
  try {
    if (!isNonBlank(signatureB64url)) return false;
    const key = await crypto.subtle.importKey(
      "raw",
      b64urlToBytes(macKeyB64url),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"],
    );
    return await crypto.subtle.verify("HMAC", key, b64urlToBytes(signatureB64url), utf8(bodyText));
  } catch {
    return false;
  }
}

const utf8 = (s) => new TextEncoder().encode(s);

/** Hex SHA-256 of a string — the dedupe key for stored history rows (idempotent re-sends). */
async function sha256Hex(s) {
  const digest = await crypto.subtle.digest("SHA-256", utf8(s));
  const bytes = new Uint8Array(digest);
  let hex = "";
  for (let i = 0; i < bytes.length; i++) hex += bytes[i].toString(16).padStart(2, "0");
  return hex;
}

function base64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlToBytes(s) {
  const std = s.replace(/-/g, "+").replace(/_/g, "/");
  const pad = std.length % 4 === 0 ? "" : "=".repeat(4 - (std.length % 4));
  return base64ToBytes(std + pad);
}

function b64urlFromBytes(bytes) {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// ---------------------------------------------------------------------------
// Misc helpers
// ---------------------------------------------------------------------------

const roomKey = (roomId) => `room:${roomId}`;
const isNonBlank = (v) => typeof v === "string" && v.trim().length > 0;
const nowSeconds = () => Math.floor(Date.now() / 1000);

/**
 * Bring a KV room record to the canonical `{ macKey, devices: [{token, label, ts}] }` shape,
 * migrating the older `{ macKey, tokens: [...] }` form on read (so no offline migration is needed).
 * Returns the same object (mutated) for chaining, or the falsy input untouched (unknown room).
 */
function normalizeRoom(room) {
  if (!room) return room;
  if (!Array.isArray(room.devices)) {
    const tokens = Array.isArray(room.tokens) ? room.tokens : [];
    room.devices = tokens.map((token) => ({ token, label: "", ts: 0 }));
  }
  if (!Array.isArray(room.events)) room.events = []; // pair/unpair audit log
  delete room.tokens; // canonical shape carries devices[] only
  return room;
}

/** Append one pair/unpair event to a room's audit log, keeping only the most recent EVENTS_MAX. */
const EVENTS_MAX = 100;
function pushEvent(room, type, token, label) {
  if (!Array.isArray(room.events)) room.events = [];
  room.events.push({ type, token, label: label || "", ts: nowSeconds() });
  if (room.events.length > EVENTS_MAX) room.events = room.events.slice(-EVENTS_MAX);
}
const parseJson = (t) => {
  try {
    return JSON.parse(t);
  } catch {
    return null;
  }
};
const safeText = async (resp) => {
  try {
    return await resp.text();
  } catch {
    return "";
  }
};

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), { status, headers: { "Content-Type": "application/json" } });
const text = (body, status = 200) =>
  new Response(body, { status, headers: { "Content-Type": "text/plain; charset=utf-8" } });
const html = (body, status = 200) =>
  new Response(body, { status, headers: { "Content-Type": "text/html; charset=utf-8" } });
const js = (body, status = 200) =>
  new Response(body, { status, headers: { "Content-Type": "text/javascript; charset=utf-8" } });
