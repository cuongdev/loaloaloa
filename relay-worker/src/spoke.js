/**
 * Web spoke + dashboard — a PWA the relay's Cloudflare Worker serves at /web, so an employee can
 * hear a shop's transactions in a browser without installing the Android app, AND see the same
 * history + reports the phone shows, plus manage paired devices.
 *
 * It is a full spoke peer: it joins a room by pairing token, registers an *FCM web push token* with
 * the Sender (just another token in the room's fan-out set), receives the same AES-256-GCM `blob`,
 * decrypts it on-device with the room secret, and announces it with the Web Speech API. The crypto
 * here mirrors the app's `RelayCrypto` byte-for-byte: two labeled HMAC-SHA256 subkeys
 * (`tingting-relay-enc-v1` / `tingting-relay-mac-v1`) off the 256-bit room secret,
 * AES-GCM(iv ‖ ct+tag), base64url everywhere.
 *
 * Tabs: Loa (live announce + settings), Lịch sử (full history), Báo cáo (income reports), Thiết bị
 * (paired-device list + pair/unpair audit log + revoke). The dashboard (Approach B): the relay logs
 * every transaction's ciphertext to D1 (see worker.js); this page pulls those rows back via the
 * signed POST /history, decrypts each on-device, and rebuilds the full history list + income reports
 * (growth, charts, peak hours, per-bank, records) entirely in the browser. The math mirrors the app's
 * TimeRanges / DateLabels / ReportMetrics, computed in the browser's local timezone. The relay still
 * sees only ciphertext — the dashboard, not the server, does every decrypt.
 *
 * Layout is responsive: a single phone-width column on small screens, widening to a multi-column
 * desktop dashboard (report sections tile 2-up) on wide screens.
 *
 * Limits (see README): audio can only play while the tab is open and after a user gesture, and a
 * closed browser can't be woken to speak — so a backgrounded push shows a notification instead. The
 * history, reports, and device management work regardless of FCM (they need only the room secret +
 * /history + /devices), so the dashboard is useful even before "Bật loa" is configured.
 *
 * These are returned as strings (no build step). spokeAppJs() is a template literal: it interpolates
 * `${FIREBASE_VERSION}` for the SDK import URLs, and the embedded app code itself never uses backticks
 * or `${...}` so nothing else interpolates.
 *
 * @param env  Worker env. `FIREBASE_WEB_CONFIG` (JSON of the Firebase *web* app config) and
 *             `VAPID_PUBLIC_KEY` (Web Push certificate public key) drive FCM; both are public, not
 *             secrets. Unset → the page loads but "Bật loa" is disabled with a config hint.
 */

const FIREBASE_VERSION = "10.12.2";

/** The PWA shell. All logic lives in /web/app.js (loaded as a module). */
export function spokeIndexHtml() {
  return '<!doctype html>\n' +
'<html lang="vi">\n' +
'<head>\n' +
'<meta charset="utf-8" />\n' +
'<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />\n' +
'<meta name="theme-color" content="#ffffff" />\n' +
'<link rel="manifest" href="/web/manifest.webmanifest" />\n' +
'<link rel="preconnect" href="https://fonts.googleapis.com" />\n' +
'<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />\n' +
'<link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700;800&display=swap" rel="stylesheet" />\n' +
'<title>Loa Loa Loa — nghe trên web</title>\n' +
'<style>\n' +
'  :root { color-scheme: light; --bg:#f4f5f7; --surface:#ffffff; --surface-2:#f7f8fa; --border:#e6e8ec; --border-2:#eef0f3; --text:#1a1d24; --muted:#6b7280; --faint:#9aa1ad; --accent:#4f46e5; --accent-hover:#4338ca; --accent-soft:#eef0fe; --green:#0ea766; --red:#e5484d; --orange:#d9730d; --shadow:0 1px 2px rgba(16,24,40,.04),0 6px 20px rgba(16,24,40,.06); }\n' +
'  * { box-sizing:border-box; }\n' +
'  body { margin:0; font-family:"Be Vietnam Pro",-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; background:var(--bg); color:var(--text); min-height:100vh; display:flex; align-items:flex-start; justify-content:center; padding:24px; -webkit-font-smoothing:antialiased; }\n' +
'  .card { width:100%; max-width:460px; margin:0 auto; background:var(--surface); border:1px solid var(--border); border-radius:22px; padding:30px 26px; box-shadow:var(--shadow); }\n' +
'  .logo { font-size:42px; line-height:1; text-align:center; }\n' +
'  h1 { font-size:24px; font-weight:800; letter-spacing:-.025em; margin:12px 0 4px; text-align:center; }\n' +
'  .tag { color:var(--muted); font-size:14px; line-height:1.55; margin:0 0 22px; text-align:center; }\n' +
'  .btn { display:block; width:100%; padding:13px 16px; border-radius:12px; font-size:15px; font-family:inherit; font-weight:600; border:0; cursor:pointer; margin-top:10px; transition:background .15s,border-color .15s,transform .05s,box-shadow .15s; }\n' +
'  .btn:active { transform:translateY(1px); }\n' +
'  .primary { background:var(--accent); color:#fff; box-shadow:0 1px 2px rgba(79,70,229,.25); }\n' +
'  .primary:hover { background:var(--accent-hover); }\n' +
'  .primary.listening { background:var(--green); box-shadow:0 1px 2px rgba(14,167,102,.3); }\n' +
'  .primary.listening:hover { background:#0c9258; }\n' +
'  .ghost { background:var(--surface); color:var(--text); border:1px solid var(--border); }\n' +
'  .ghost:hover { background:var(--surface-2); border-color:#d6d9df; }\n' +
'  .danger { background:var(--surface); color:var(--red); border:1px solid #f1c7c8; }\n' +
'  .danger:hover { background:#fdf2f2; }\n' +
'  input { width:100%; padding:13px 14px; border-radius:11px; border:1px solid var(--border); background:var(--surface); color:var(--text); font-size:14px; font-family:inherit; transition:border-color .15s,box-shadow .15s; }\n' +
'  input::placeholder { color:var(--faint); }\n' +
'  input:focus { outline:none; border-color:var(--accent); box-shadow:0 0 0 3px var(--accent-soft); }\n' +
'  label.row { display:flex; align-items:center; gap:11px; padding:13px 2px; font-size:15px; color:var(--text); border-top:1px solid var(--border-2); cursor:pointer; }\n' +
'  label.row input { width:18px; height:18px; accent-color:var(--accent); }\n' +
'  .status { font-size:14px; color:var(--muted); background:var(--surface-2); border:1px solid var(--border); border-radius:12px; padding:13px 14px; margin-bottom:14px; line-height:1.55; }\n' +
'  .warn { background:#fff8ec; border:1px solid #f5e0b5; color:#92590a; border-radius:12px; padding:12px 14px; font-size:13px; margin-bottom:12px; line-height:1.5; }\n' +
'  .room { font-size:12.5px; color:var(--faint); margin:2px 0 16px; word-break:break-all; }\n' +
'  ul { list-style:none; padding:0; margin:14px 0 0; }\n' +
'  li { border-top:1px solid var(--border-2); padding:12px 2px; font-size:14px; display:flex; justify-content:space-between; gap:12px; }\n' +
'  li .amt { font-weight:700; white-space:nowrap; }\n' +
'  li .amt.in { color:var(--green); }\n' +
'  li .amt.out { color:var(--orange); }\n' +
'  li .meta { color:var(--muted); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }\n' +
'  .hidden { display:none; }\n' +
'  .note { color:var(--faint); font-size:12px; margin-top:18px; line-height:1.55; }\n' +
'  h2 { font-size:11.5px; font-weight:700; text-transform:uppercase; letter-spacing:.07em; color:var(--faint); margin:22px 0 4px; }\n' +
'  h3 { font-size:11.5px; font-weight:700; text-transform:uppercase; letter-spacing:.07em; color:var(--faint); margin:22px 0 8px; }\n' +
'  .tabs { display:flex; gap:4px; margin:8px 0 18px; background:var(--surface-2); border:1px solid var(--border); padding:4px; border-radius:13px; }\n' +
'  .tab { flex:1; padding:9px 4px; border-radius:9px; border:0; background:transparent; color:var(--muted); font-size:13px; font-weight:600; font-family:inherit; cursor:pointer; white-space:nowrap; transition:background .15s,color .15s,box-shadow .15s; }\n' +
'  .tab:hover { color:var(--text); }\n' +
'  .tab.active { background:var(--surface); color:var(--accent); box-shadow:0 1px 2px rgba(16,24,40,.08); }\n' +
'  .chips { display:flex; gap:6px; margin:4px 0 16px; }\n' +
'  .chip { flex:1; padding:8px 6px; border-radius:999px; border:1px solid var(--border); background:var(--surface); color:var(--muted); font-size:13px; font-weight:500; font-family:inherit; cursor:pointer; transition:all .15s; }\n' +
'  .chip:hover { border-color:#d6d9df; }\n' +
'  .chip.active { background:var(--accent); border-color:var(--accent); color:#fff; }\n' +
'  .empty { color:var(--faint); font-size:14px; text-align:center; padding:30px 8px; line-height:1.5; }\n' +
'  .hero { background:var(--surface-2); border:1px solid var(--border); border-radius:16px; padding:22px; text-align:center; }\n' +
'  .hero .big { font-size:30px; font-weight:800; letter-spacing:-.02em; color:var(--green); }\n' +
'  .hero .sub { color:var(--muted); font-size:13px; margin-top:6px; }\n' +
'  .grid3 { display:flex; gap:8px; }\n' +
'  .gcard { flex:1; background:var(--surface-2); border:1px solid var(--border); border-radius:13px; padding:14px 11px; }\n' +
'  .glabel { color:var(--faint); font-size:11px; font-weight:500; }\n' +
'  .gamt { font-size:15px; font-weight:700; margin:5px 0; white-space:nowrap; }\n' +
'  .gdelta { font-size:13px; font-weight:600; }\n' +
'  .gdelta.up { color:var(--green); } .gdelta.down { color:var(--red); } .gdelta.flat { color:var(--muted); } .gdelta.new { color:var(--accent); } .gdelta.none { color:var(--faint); }\n' +
'  .chart { display:flex; align-items:flex-end; gap:3px; height:120px; padding-top:6px; }\n' +
'  .bar { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:flex-end; height:100%; min-width:0; }\n' +
'  .barfill { width:70%; max-width:22px; background:#dfe2ea; border-radius:6px 6px 0 0; min-height:2px; transition:height .3s; }\n' +
'  .bar.hl .barfill { background:var(--accent); }\n' +
'  .barlbl { font-size:9px; color:var(--faint); margin-top:5px; white-space:nowrap; overflow:hidden; max-width:100%; }\n' +
'  .bankrow { display:flex; align-items:center; gap:10px; padding:9px 0; font-size:14px; border-top:1px solid var(--border-2); }\n' +
'  .bankrow .bn { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }\n' +
'  .bankrow .bv { color:var(--muted); white-space:nowrap; }\n' +
'  .bankrow .bp { width:42px; text-align:right; color:var(--green); font-weight:700; }\n' +
'  .records { display:flex; gap:8px; }\n' +
'  .recrow { flex:1; background:var(--surface-2); border:1px solid var(--border); border-radius:13px; padding:14px; }\n' +
'  .recrow .rl { color:var(--faint); font-size:11px; font-weight:500; }\n' +
'  .recrow .rv { font-size:16px; font-weight:700; margin-top:4px; }\n' +
'  .headline { color:var(--muted); font-size:13px; margin:0 0 8px; }\n' +
'  .hrow { display:flex; justify-content:space-between; gap:12px; border-top:1px solid var(--border-2); padding:11px 2px; }\n' +
'  .hmeta { min-width:0; flex:1; }\n' +
'  .hbank { font-size:14px; font-weight:500; }\n' +
'  .htime { color:var(--faint); font-size:12px; line-height:1.4; overflow-wrap:anywhere; }\n' +
'  .hamt { font-weight:700; white-space:nowrap; flex-shrink:0; }\n' +
'  .hamt.in { color:var(--green); } .hamt.out { color:var(--orange); }\n' +
'  .daygroup { margin:18px 0 2px; }\n' +
'  .dev { border-top:1px solid var(--border-2); padding:13px 2px; }\n' +
'  .dev .top { display:flex; justify-content:space-between; align-items:center; gap:10px; }\n' +
'  .dev .dl { font-size:15px; font-weight:500; }\n' +
'  .dev .badge { font-size:11px; font-weight:600; color:var(--accent); background:var(--accent-soft); border-radius:999px; padding:2px 9px; margin-left:6px; }\n' +
'  .dev .dt { color:var(--faint); font-size:12px; margin-top:3px; }\n' +
'  .dev .revoke { background:var(--surface); color:var(--red); border:1px solid #f1c7c8; border-radius:9px; padding:6px 12px; font-size:13px; font-family:inherit; cursor:pointer; white-space:nowrap; }\n' +
'  .dev .revoke:hover { background:#fdf2f2; }\n' +
'  .evt { display:flex; gap:10px; align-items:baseline; border-top:1px solid var(--border-2); padding:9px 2px; font-size:13px; }\n' +
'  .evt.pair .ei { color:var(--green); } .evt.unpair .ei { color:var(--red); }\n' +
'  .evt .el { flex:1; color:var(--text); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }\n' +
'  .evt .et { color:var(--faint); font-size:12px; white-space:nowrap; }\n' +
'  @media (min-width:640px) {\n' +
'    body { align-items:center; padding:40px; }\n' +
'    .card { max-width:520px; padding:36px 40px; }\n' +
'    .chart { height:150px; }\n' +
'  }\n' +
'</style>\n' +
'<script type="text/javascript">(function(c,l,a,r,i,t,y){c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y);})(window,document,"clarity","script","x0p7lv1ahl");</script>\n' +
'</head>\n' +
'<body>\n' +
'  <div class="card">\n' +
'    <div class="logo"><svg width="56" height="56" viewBox="0 0 64 64" role="img" aria-label="Loa Loa Loa"><g fill="#0EA5A4"><path d="M6 26h8v12H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z"/><path d="M14 24 42 14v36L14 40z"/><path d="M20 40h6v9a3 3 0 0 1-6 0z"/></g><path d="M48 24q6 8 0 16" fill="none" stroke="#0EA5A4" stroke-width="3.5" stroke-linecap="round"/><path d="M54 19q10 13 0 26" fill="none" stroke="#5EEAD4" stroke-width="3.5" stroke-linecap="round"/></svg></div>\n' +
'    <h1>Loa Loa Loa</h1>\n' +
'    <p class="tag">Nghe giao dịch của shop trên trình duyệt — không cần đăng nhập ngân hàng.</p>\n' +
'\n' +
'    <div id="status" class="status">Đang tải…</div>\n' +
'    <div id="cfgwarn" class="warn hidden">Máy chủ chưa bật Firebase web (FIREBASE_WEB_CONFIG / VAPID_PUBLIC_KEY) nên không nghe trực tiếp được — nhưng Lịch sử + Báo cáo + Thiết bị vẫn xem được.</div>\n' +
'\n' +
'    <div id="pairView" class="hidden">\n' +
'      <input id="tokenInput" placeholder="Dán mã ghép (hoặc link /pair#...) từ máy shop" />\n' +
'      <button id="pairBtn" class="btn primary">Ghép phòng</button>\n' +
'      <button id="scanBtn" class="btn ghost hidden">Quét mã QR bằng camera</button>\n' +
'      <video id="cam" class="hidden" playsinline style="width:100%;border-radius:12px;margin-top:10px"></video>\n' +
'    </div>\n' +
'\n' +
'    <div id="appView" class="hidden">\n' +
'      <div id="room" class="room"></div>\n' +
'      <nav class="tabs">\n' +
'        <button class="tab active" data-tab="loa">Loa</button>\n' +
'        <button class="tab" data-tab="history">Lịch sử</button>\n' +
'        <button class="tab" data-tab="report">Báo cáo</button>\n' +
'        <button class="tab" data-tab="devices">Thiết bị</button>\n' +
'      </nav>\n' +
'\n' +
'      <section id="tab-loa">\n' +
'        <button id="enableBtn" class="btn primary">Bật loa (cho phép thông báo + âm thanh)</button>\n' +
'        <button id="testBtn" class="btn ghost">Đọc thử</button>\n' +
'        <h2>Cài đặt</h2>\n' +
'        <label class="row"><input type="checkbox" id="optShort" /> Tin nhắn rút gọn (bỏ tên ngân hàng)</label>\n' +
'        <label class="row"><input type="checkbox" id="optRepeat" /> Đọc lại 2 lần</label>\n' +
'        <label class="row"><input type="checkbox" id="optIncome" /> Chỉ đọc tiền vào</label>\n' +
'        <h2>Gần đây</h2>\n' +
'        <ul id="list"></ul>\n' +
'        <button id="unpairBtn" class="btn danger">Huỷ ghép máy này</button>\n' +
'      </section>\n' +
'\n' +
'      <section id="tab-history" class="hidden">\n' +
'        <div id="historyBody"></div>\n' +
'        <button id="refreshHistBtn" class="btn ghost" style="margin-top:16px">Tải lại dữ liệu</button>\n' +
'      </section>\n' +
'\n' +
'      <section id="tab-report" class="hidden">\n' +
'        <div class="chips">\n' +
'          <button class="chip active" data-period="TODAY">Hôm nay</button>\n' +
'          <button class="chip" data-period="WEEK">Tuần này</button>\n' +
'          <button class="chip" data-period="MONTH">Tháng này</button>\n' +
'        </div>\n' +
'        <div id="reportBody"></div>\n' +
'        <button id="refreshBtn" class="btn ghost" style="margin-top:16px">Tải lại dữ liệu</button>\n' +
'      </section>\n' +
'\n' +
'      <section id="tab-devices" class="hidden">\n' +
'        <div id="devicesBody"></div>\n' +
'        <button id="refreshDevBtn" class="btn ghost" style="margin-top:16px">Tải lại</button>\n' +
'      </section>\n' +
'    </div>\n' +
'\n' +
'    <p class="note">Giữ tab này mở để nghe. Trình duyệt đóng/ngủ sẽ không đọc được (chỉ hiện thông báo). Mã ghép chỉ đọc trên máy bạn — máy chủ không nhận được.</p>\n' +
'  </div>\n' +
'  <script type="module" src="/web/app.js"></script>\n' +
'</body>\n' +
'</html>';
}

/** The ESM app: crypto (mirrors RelayCrypto), pairing, FCM web token, decrypt, TTS, history, reports, devices, UI. */
export function spokeAppJs() {
  return `import { initializeApp } from 'https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-app.js';
import { getMessaging, getToken, onMessage } from 'https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-messaging.js';
import { firebaseConfig, vapidKey, configured } from './config.js';

var enc = new TextEncoder();
var dec = new TextDecoder();
var ROOM_KEY = 'loaloaloa_room';
var SEEN_KEY = 'loaloaloa_seen';
var OPT_KEY = 'loaloaloa_opts';
var TOKEN_KEY = 'loaloaloa_token';

// ---- base64url ----
function b64urlToBytes(s){ s = String(s).trim().replace(/-/g,'+').replace(/_/g,'/'); var pad = s.length % 4 === 0 ? '' : '='.repeat(4 - (s.length % 4)); var bin = atob(s + pad); var out = new Uint8Array(bin.length); for (var i=0;i<bin.length;i++) out[i] = bin.charCodeAt(i); return out; }
function b64urlFromBytes(bytes){ var bin=''; for (var i=0;i<bytes.length;i++) bin += String.fromCharCode(bytes[i]); return btoa(bin).replace(/\\+/g,'-').replace(/\\//g,'_').replace(/=+$/,''); }

// ---- crypto (mirrors RelayCrypto) ----
async function hmac(keyBytes, dataBytes){ var k = await crypto.subtle.importKey('raw', keyBytes, {name:'HMAC',hash:'SHA-256'}, false, ['sign']); var sig = await crypto.subtle.sign('HMAC', k, dataBytes); return new Uint8Array(sig); }
async function subKey(secretBytes, label){ return hmac(secretBytes, enc.encode(label)); }
async function macKeyB64(secretBytes){ return b64urlFromBytes(await subKey(secretBytes, 'tingting-relay-mac-v1')); }
async function signBody(secretBytes, bodyStr){ var mac = await subKey(secretBytes, 'tingting-relay-mac-v1'); return b64urlFromBytes(await hmac(mac, enc.encode(bodyStr))); }
async function decryptBlob(secretBytes, blob){ try { var encKey = await subKey(secretBytes, 'tingting-relay-enc-v1'); var raw = b64urlToBytes(blob); if (raw.length <= 12) return null; var iv = raw.slice(0,12); var body = raw.slice(12); var k = await crypto.subtle.importKey('raw', encKey, {name:'AES-GCM'}, false, ['decrypt']); var pt = await crypto.subtle.decrypt({name:'AES-GCM', iv: iv, tagLength: 128}, k, body); return dec.decode(pt); } catch (e) { return null; } }

// ---- signed POST helper (shared by /history, /devices, /revoke) ----
async function signedPost(path, bodyObj){ var secretBytes = b64urlToBytes(currentRoom.roomSecret); var url = String(currentRoom.senderUrl).replace(/\\/$/, '') + path; var bodyStr = JSON.stringify(bodyObj); var sig = await signBody(secretBytes, bodyStr); return fetch(url, { method:'POST', headers:{ 'Content-Type':'application/json', 'X-Relay-Signature': sig }, body: bodyStr }); }

// ---- pairing ----
function decodePairing(token){ try { var raw = String(token).trim(); var h = raw.lastIndexOf('#'); var payload = h >= 0 ? raw.slice(h+1) : raw; var p = JSON.parse(dec.decode(b64urlToBytes(payload))); if (p && p.roomId && p.roomSecret && p.senderUrl) return p; return null; } catch (e) { return null; } }
function loadRoom(){ try { return JSON.parse(localStorage.getItem(ROOM_KEY)); } catch (e) { return null; } }
function saveRoom(r){ localStorage.setItem(ROOM_KEY, JSON.stringify(r)); }
function clearRoom(){ localStorage.removeItem(ROOM_KEY); localStorage.removeItem(SEEN_KEY); localStorage.removeItem(TOKEN_KEY); }
function deviceLabel(){ var p = (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || 'trình duyệt'; return 'Web · ' + p; }

// ---- announce dedupe (capped; speak each tx once) ----
var seen = new Set();
(function(){ try { (JSON.parse(localStorage.getItem(SEEN_KEY)) || []).forEach(function(id){ seen.add(id); }); } catch (e) {} })();
function remember(id){ seen.add(id); var arr = Array.from(seen).slice(-50); localStorage.setItem(SEEN_KEY, JSON.stringify(arr)); }

// ---- transaction store (uncapped; feeds history + reports) ----
var txMap = new Map();
function recordTx(p){ if (!p || !p.txId) return false; var isNew = !txMap.has(p.txId); txMap.set(p.txId, p); return isNew; }
function txListDesc(){ return Array.from(txMap.values()).sort(function(a,b){ return (b.ts||0) - (a.ts||0); }); }

// ---- settings ----
function loadOpts(){ try { return Object.assign({ shortMessage:false, repeat:false, incomeOnly:false }, JSON.parse(localStorage.getItem(OPT_KEY)) || {}); } catch (e) { return { shortMessage:false, repeat:false, incomeOnly:false }; } }
function saveOpts(o){ localStorage.setItem(OPT_KEY, JSON.stringify(o)); }
var opts = loadOpts();

// ---- formatting ----
function formatAmount(n){ var d = String(n); var out = ''; var first = d.length % 3; for (var i=0;i<d.length;i++){ if (i !== 0 && (i - first) % 3 === 0) out += '.'; out += d[i]; } return out; }
function fmtVnd(n){ return formatAmount(n) + ' đ'; }
function esc(s){ return String(s == null ? '' : s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
function pad2(n){ return n < 10 ? '0' + n : '' + n; }
function timeLabel(ts){ var d = new Date(ts); return pad2(d.getHours()) + ':' + pad2(d.getMinutes()); }
function fmtDateTime(tsSec){ if (!tsSec) return '—'; try { return new Date(tsSec * 1000).toLocaleString('vi-VN'); } catch (e) { return '—'; } }

// ---- UI plumbing ----
var $ = function(id){ return document.getElementById(id); };
function status(msg){ $('status').textContent = msg; }
function show(id, on){ var el = $(id); if (el) el.classList.toggle('hidden', !on); }
function addToList(p){ var li = document.createElement('li'); var amt = document.createElement('span'); amt.className = 'amt ' + (p.type === 'in' ? 'in' : 'out'); amt.textContent = (p.type === 'in' ? '+' : '-') + formatAmount(p.amount); var meta = document.createElement('span'); meta.className = 'meta'; meta.textContent = (p.bank || '') + ' · ' + new Date(p.ts || Date.now()).toLocaleTimeString('vi-VN'); li.appendChild(meta); li.appendChild(amt); var ul = $('list'); if (!ul) return; ul.insertBefore(li, ul.firstChild); while (ul.children.length > 20) ul.removeChild(ul.lastChild); }

// ---- TTS (mirrors SpeechTextBuilder) ----
function buildSentence(p){ var amount = formatAmount(p.amount); var includeBank = !opts.shortMessage && p.bank && p.bank.length > 0; if (p.type === 'in') { return includeBank ? ('Loa loa loa! Bạn vừa nhận được ' + p.bank + ' ' + amount + ' đồng') : ('Loa loa loa! Bạn vừa nhận được ' + amount + ' đồng'); } return includeBank ? ('Bạn vừa chuyển ' + p.bank + ' ' + amount + ' đồng') : ('Bạn vừa chuyển ' + amount + ' đồng'); }
function pickVoice(){ var vs = speechSynthesis.getVoices(); for (var i=0;i<vs.length;i++){ if (/^vi/i.test(vs[i].lang)) return vs[i]; } return null; }
function speak(text, repeat){ try { var v = pickVoice(); var u = new SpeechSynthesisUtterance(text); u.lang = 'vi-VN'; if (v) u.voice = v; speechSynthesis.speak(u); if (repeat) { var u2 = new SpeechSynthesisUtterance(text); u2.lang = 'vi-VN'; if (v) u2.voice = v; speechSynthesis.speak(u2); } } catch (e) {} }
try { speechSynthesis.onvoiceschanged = function(){ speechSynthesis.getVoices(); }; } catch (e) {}

// ---- date / range helpers (mirror TimeRanges + DateLabels, in the browser's local zone) ----
function dateOfTs(ts){ var x = new Date(ts); return new Date(x.getFullYear(), x.getMonth(), x.getDate()); }
function todayDate(){ return dateOfTs(Date.now()); }
function addDays(dt, n){ return new Date(dt.getFullYear(), dt.getMonth(), dt.getDate() + n); }
function ymd(dt){ return dt.getFullYear() * 10000 + (dt.getMonth() + 1) * 100 + dt.getDate(); }
function sameDay(a, b){ return ymd(a) === ymd(b); }
function startMs(dt){ return new Date(dt.getFullYear(), dt.getMonth(), dt.getDate()).getTime(); }
function dayRange(dt){ return { from: startMs(dt), to: startMs(addDays(dt, 1)) - 1 }; }
function rangeOfDays(a, b){ return { from: startMs(a), to: startMs(addDays(b, 1)) - 1 }; }
function isoDow(dt){ var d = dt.getDay(); return d === 0 ? 7 : d; }
function mondayOf(dt){ return addDays(dt, -(isoDow(dt) - 1)); }
function weekRange(dt){ var m = mondayOf(dt); return rangeOfDays(m, addDays(m, 6)); }
function firstOfMonth(dt){ return new Date(dt.getFullYear(), dt.getMonth(), 1); }
function monthRange(dt){ return { from: new Date(dt.getFullYear(), dt.getMonth(), 1).getTime(), to: new Date(dt.getFullYear(), dt.getMonth() + 1, 1).getTime() - 1 }; }
function daysInMonth(dt){ return new Date(dt.getFullYear(), dt.getMonth() + 1, 0).getDate(); }
function weekdayShort(dow){ return ['','T2','T3','T4','T5','T6','T7'][dow] || 'CN'; }
function weekdayFull(dow){ return ['','Thứ 2','Thứ 3','Thứ 4','Thứ 5','Thứ 6','Thứ 7'][dow] || 'Chủ nhật'; }
function groupLabel(date, today){ if (sameDay(date, today)) return 'Hôm nay'; if (sameDay(date, addDays(today, -1))) return 'Hôm qua'; return date.getDate() + ' thg ' + (date.getMonth() + 1); }

// ---- report aggregation (mirrors ReportMetrics; income-only) ----
function sumIn(income, range){ var s = 0; for (var i=0;i<income.length;i++){ var t = income[i]; if (t.ts >= range.from && t.ts <= range.to) s += t.amount; } return s; }
function spansOf(span, today){ if (span === 'DAY') return { cur: dayRange(today), prev: dayRange(addDays(today, -1)) }; if (span === 'WEEK'){ var weekStart = mondayOf(today); var daysIn = isoDow(today) - 1; var prevStart = addDays(weekStart, -7); return { cur: rangeOfDays(weekStart, today), prev: rangeOfDays(prevStart, addDays(prevStart, daysIn)) }; } var monthStart = firstOfMonth(today); var lastMonthStart = new Date(today.getFullYear(), today.getMonth() - 1, 1); var prevEndDay = Math.min(today.getDate(), daysInMonth(lastMonthStart)); var prevEnd = new Date(lastMonthStart.getFullYear(), lastMonthStart.getMonth(), prevEndDay); return { cur: rangeOfDays(monthStart, today), prev: rangeOfDays(lastMonthStart, prevEnd) }; }
function growthOf(cur, prev){ if (prev === 0 && cur === 0) return { pct: null, status: 'NONE' }; if (prev === 0) return { pct: null, status: 'NEW' }; if (cur === 0) return { pct: -100, status: 'DOWN' }; var pct = Math.round((cur - prev) * 100 / prev); var st = pct > 0 ? 'UP' : (pct < 0 ? 'DOWN' : 'FLAT'); return { pct: pct, status: st }; }
function growthCard(label, income, today, span){ var s = spansOf(span, today); var cur = sumIn(income, s.cur); var prev = sumIn(income, s.prev); var g = growthOf(cur, prev); return { label: label, currentAmount: cur, deltaPercent: g.pct, status: g.status }; }
function periodBars(period, income, today){ var bars = []; var i, day; if (period === 'TODAY'){ for (i=0;i<7;i++){ day = addDays(today, -(6 - i)); bars.push({ label: weekdayShort(isoDow(day)), value: sumIn(income, dayRange(day)), highlight: sameDay(day, today) }); } } else if (period === 'WEEK'){ var monday = mondayOf(today); for (i=0;i<7;i++){ day = addDays(monday, i); bars.push({ label: weekdayShort(isoDow(day)), value: sumIn(income, dayRange(day)), highlight: sameDay(day, today) }); } } else { var n = daysInMonth(today); for (i=1;i<=n;i++){ day = new Date(today.getFullYear(), today.getMonth(), i); var label = (i === 1 || i % 5 === 0) ? String(i) : ''; bars.push({ label: label, value: sumIn(income, dayRange(day)), highlight: sameDay(day, today) }); } } return bars; }
function monthlyTrend(income, today){ var bars = []; for (var back=5; back>=0; back--){ var m = new Date(today.getFullYear(), today.getMonth() - back, 1); bars.push({ label: 'thg ' + (m.getMonth() + 1), value: sumIn(income, monthRange(m)), highlight: back === 0 }); } return bars; }
function perBank(periodRecords, periodTotal){ if (periodTotal <= 0) return []; var map = {}; var order = []; for (var i=0;i<periodRecords.length;i++){ var t = periodRecords[i]; var b = t.bank || ''; if (!(b in map)){ map[b] = 0; order.push(b); } map[b] += t.amount; } var arr = order.map(function(b){ return { bankName: b, income: map[b] }; }); arr.sort(function(a,b){ return b.income - a.income; }); var top = arr.slice(0, 5); var rest = 0; for (var j=5;j<arr.length;j++) rest += arr[j].income; var slices = top.map(function(s){ return { bankName: s.bankName, income: s.income, percent: Math.round(s.income * 100 / periodTotal) }; }); if (rest > 0) slices.push({ bankName: 'Khác', income: rest, percent: Math.round(rest * 100 / periodTotal) }); return slices; }
function recordsOf(income, today){ if (!income.length) return { bestDayLabel: null, bestDayAmount: 0, biggestTransaction: 0 }; var biggest = 0; var byDay = {}; for (var i=0;i<income.length;i++){ var t = income[i]; if (t.amount > biggest) biggest = t.amount; var d = dateOfTs(t.ts); var k = ymd(d); if (!byDay[k]) byDay[k] = { total: 0, date: d }; byDay[k].total += t.amount; } var best = null; for (var k2 in byDay){ if (!best || byDay[k2].total > best.total) best = byDay[k2]; } return { bestDayLabel: best ? groupLabel(best.date, today) : null, bestDayAmount: best ? best.total : 0, biggestTransaction: biggest }; }
function computeReport(period){ var today = todayDate(); var all = txListDesc(); var income = all.filter(function(t){ return t.type === 'in'; }); var pr = period === 'TODAY' ? dayRange(today) : (period === 'WEEK' ? weekRange(today) : monthRange(today)); var periodRecords = income.filter(function(t){ return t.ts >= pr.from && t.ts <= pr.to; }); var periodTotal = 0; for (var i=0;i<periodRecords.length;i++) periodTotal += periodRecords[i].amount; var periodCount = periodRecords.length; var averageTicket = periodCount > 0 ? Math.floor(periodTotal / periodCount) : 0; var patternRange = rangeOfDays(addDays(today, -29), today); var patternRecords = income.filter(function(t){ return t.ts >= patternRange.from && t.ts <= patternRange.to; }); var peakHours = []; for (var h=0;h<24;h++){ var hs = 0; for (var j=0;j<patternRecords.length;j++){ if (new Date(patternRecords[j].ts).getHours() === h) hs += patternRecords[j].amount; } peakHours.push({ hour: h, income: hs }); } var topHour = null; for (var h2=0;h2<24;h2++){ if (peakHours[h2].income > 0 && (!topHour || peakHours[h2].income > topHour.income)) topHour = peakHours[h2]; } var peakHourHeadline = topHour ? ('Đông khách nhất: ' + topHour.hour + '–' + ((topHour.hour + 1) % 24) + 'h') : null; var wd = {}; for (var w=0;w<patternRecords.length;w++){ var dow = isoDow(dateOfTs(patternRecords[w].ts)); wd[dow] = (wd[dow] || 0) + patternRecords[w].amount; } var busiestWeekday = []; for (var dw=1;dw<=7;dw++) busiestWeekday.push({ label: weekdayShort(dw), income: wd[dw] || 0 }); var topDow = 0; for (var dw2=1;dw2<=7;dw2++){ if ((wd[dw2] || 0) > 0 && (topDow === 0 || (wd[dw2] || 0) > (wd[topDow] || 0))) topDow = dw2; } var busiestWeekdayHeadline = topDow ? ('Bận nhất: ' + weekdayFull(topDow)) : null; return { period: period, periodTotal: periodTotal, periodCount: periodCount, averageTicket: averageTicket, growth: [ growthCard('vs hôm qua', income, today, 'DAY'), growthCard('vs tuần rồi', income, today, 'WEEK'), growthCard('vs tháng rồi', income, today, 'MONTH') ], periodBars: periodBars(period, income, today), monthlyTrend: monthlyTrend(income, today), peakHours: peakHours, peakHourHeadline: peakHourHeadline, busiestWeekday: busiestWeekday, busiestWeekdayHeadline: busiestWeekdayHeadline, perBank: perBank(periodRecords, periodTotal), records: recordsOf(income, today), hasData: income.length > 0 }; }

// ---- report rendering ----
function barsHtml(bars){ var max = 0; for (var i=0;i<bars.length;i++) if (bars[i].value > max) max = bars[i].value; var cols = ''; for (var j=0;j<bars.length;j++){ var b = bars[j]; var hpct = max > 0 ? Math.round(b.value * 100 / max) : 0; cols += '<div class="bar' + (b.highlight ? ' hl' : '') + '" title="' + esc(fmtVnd(b.value)) + '"><div class="barfill" style="height:' + hpct + '%"></div><span class="barlbl">' + esc(b.label) + '</span></div>'; } return '<div class="chart">' + cols + '</div>'; }
function chartHtml(title, bars){ return '<h3>' + esc(title) + '</h3>' + barsHtml(bars); }
function periodTitle(period){ return period === 'TODAY' ? '7 ngày qua' : (period === 'WEEK' ? 'tuần này' : 'tháng này'); }
function periodHeroLabel(period){ return period === 'TODAY' ? 'Hôm nay' : (period === 'WEEK' ? 'Tuần này' : 'Tháng này'); }
function heroHtml(m){ return '<div class="hero"><div class="big">' + fmtVnd(m.periodTotal) + '</div><div class="sub">' + esc(periodHeroLabel(m.period)) + ' · ' + m.periodCount + ' giao dịch · TB ' + fmtVnd(m.averageTicket) + '</div></div>'; }
function deltaText(c){ if (c.status === 'NEW') return 'Mới'; if (c.status === 'NONE') return '—'; var sign = c.deltaPercent > 0 ? '+' : ''; return sign + c.deltaPercent + '%'; }
function growthHtml(cards){ var out = '<h3>Tăng trưởng</h3><div class="grid3">'; for (var i=0;i<cards.length;i++){ var c = cards[i]; var cls = c.status.toLowerCase(); out += '<div class="gcard"><div class="glabel">' + esc(c.label) + '</div><div class="gamt">' + fmtVnd(c.currentAmount) + '</div><div class="gdelta ' + cls + '">' + esc(deltaText(c)) + '</div></div>'; } return out + '</div>'; }
function peakHtml(m){ if (!m.peakHourHeadline) return ''; return '<h3>Giờ cao điểm</h3><p class="headline">' + esc(m.peakHourHeadline) + '</p>' + barsHtml(m.peakHours.map(function(h){ return { label: (h.hour % 6 === 0 ? h.hour + 'h' : ''), value: h.income, highlight: false }; })); }
function weekdayHtml(m){ if (!m.busiestWeekdayHeadline) return ''; return '<h3>Ngày bận nhất</h3><p class="headline">' + esc(m.busiestWeekdayHeadline) + '</p>' + barsHtml(m.busiestWeekday.map(function(w){ return { label: w.label, value: w.income, highlight: false }; })); }
function perBankHtml(slices){ if (!slices.length) return ''; var out = '<h3>Theo ngân hàng</h3>'; for (var i=0;i<slices.length;i++){ var s = slices[i]; out += '<div class="bankrow"><span class="bn">' + esc(s.bankName || '(không rõ)') + '</span><span class="bv">' + fmtVnd(s.income) + '</span><span class="bp">' + s.percent + '%</span></div>'; } return out; }
function recordsHtml(r){ if (!r.bestDayLabel && !r.biggestTransaction) return ''; return '<h3>Kỷ lục</h3><div class="records"><div class="recrow"><div class="rl">Ngày cao nhất</div><div class="rv">' + esc(r.bestDayLabel || '—') + '</div><div class="sub" style="color:#9aa3b2;font-size:12px;margin-top:4px">' + fmtVnd(r.bestDayAmount) + '</div></div><div class="recrow"><div class="rl">Giao dịch lớn nhất</div><div class="rv">' + fmtVnd(r.biggestTransaction) + '</div></div></div>'; }
function rsec(inner, mod){ if (!inner) return ''; return '<div class="rsec' + (mod ? ' ' + mod : '') + '">' + inner + '</div>'; }
function renderReport(){ var body = $('reportBody'); if (!body) return; var m = computeReport(activePeriod); if (!m.hasData){ body.innerHTML = '<div class="empty">Chưa có dữ liệu thu. Khi máy shop gửi giao dịch, báo cáo sẽ hiện ở đây.</div>'; return; } body.innerHTML = rsec(heroHtml(m), 'full') + rsec(growthHtml(m.growth), 'full') + rsec(chartHtml('Doanh thu ' + periodTitle(m.period), m.periodBars)) + rsec(chartHtml('Xu hướng 6 tháng', m.monthlyTrend)) + rsec(peakHtml(m)) + rsec(weekdayHtml(m)) + rsec(perBankHtml(m.perBank)) + rsec(recordsHtml(m.records)); }

// ---- history rendering ----
function renderHistory(){ var body = $('historyBody'); if (!body) return; var all = txListDesc(); if (!all.length){ body.innerHTML = '<div class="empty">Chưa có lịch sử giao dịch.</div>'; return; } var today = todayDate(); var html = ''; var curKey = null; for (var i=0;i<all.length;i++){ var t = all[i]; var d = dateOfTs(t.ts); var key = ymd(d); if (key !== curKey){ curKey = key; html += '<h3 class="daygroup">' + esc(groupLabel(d, today)) + '</h3>'; } var sign = t.type === 'in' ? '+' : '-'; var cls = t.type === 'in' ? 'in' : 'out'; var sub = timeLabel(t.ts) + (t.content ? (' · ' + esc(t.content)) : ''); html += '<div class="hrow"><div class="hmeta"><div class="hbank">' + esc(t.bank || '(không rõ)') + '</div><div class="htime">' + sub + '</div></div><div class="hamt ' + cls + '">' + sign + formatAmount(t.amount) + '</div></div>'; } body.innerHTML = html; }

// ---- device management rendering ----
function renderDevices(devices, events){ var body = $('devicesBody'); if (!body) return; var html = '<h3>Thiết bị đang ghép (' + devices.length + ')</h3>'; if (!devices.length){ html += '<div class="empty">Chưa có thiết bị nào.</div>'; } for (var i=0;i<devices.length;i++){ var d = devices[i]; var isMe = currentToken && d.token === currentToken; var label = d.label || 'Thiết bị'; html += '<div class="dev"><div class="top"><div><span class="dl">' + esc(label) + '</span>' + (isMe ? '<span class="badge">máy này</span>' : '') + '</div><button class="revoke" data-token="' + esc(d.token) + '" data-label="' + esc(label) + '">Huỷ ghép</button></div><div class="dt">Ghép lúc ' + esc(fmtDateTime(d.ts)) + '</div></div>'; } html += '<h3>Lịch sử ghép / huỷ</h3>'; if (!events.length){ html += '<div class="empty">Chưa có lịch sử.</div>'; } for (var j=0;j<events.length;j++){ var e = events[j]; var pair = e.type === 'pair'; html += '<div class="evt ' + (pair ? 'pair' : 'unpair') + '"><span class="ei">' + (pair ? '➕' : '✕') + '</span><span class="el">' + esc(e.label || 'Thiết bị') + ' · ' + (pair ? 'Ghép' : 'Huỷ ghép') + '</span><span class="et">' + esc(fmtDateTime(e.ts)) + '</span></div>'; } body.innerHTML = html; var btns = body.querySelectorAll('.revoke'); for (var k=0;k<btns.length;k++){ btns[k].addEventListener('click', function(){ revokeDevice(this.getAttribute('data-token'), this.getAttribute('data-label')); }); } }

/** Locally unpair this browser after the hub revoked it remotely (so it stops showing "đã ghép"). */
function revokedKick(){ clearRoom(); currentRoom = null; txMap = new Map(); currentToken = null; renderPair(); status('Máy này đã bị huỷ ghép từ phòng (máy shop thu hồi). Dán mã để ghép lại.'); }

/** Poll: if this device registered a push token but is no longer in the room's device list (or the
 *  room is gone), the hub revoked it — drop the cached pairing and return to the pair screen. */
async function checkRevoked(){ if (!currentRoom) return; try { var resp = await signedPost('/devices', { roomId: currentRoom.roomId }); if (resp.status === 404){ revokedKick(); return; } if (resp.ok && currentToken){ var data = await resp.json(); if (!(data.devices || []).some(function(d){ return d.token === currentToken; })){ revokedKick(); } } } catch (e) {} }

async function loadDevices(){ if (!currentRoom) return; var body = $('devicesBody'); if (!body) return; body.innerHTML = '<div class="empty">Đang tải…</div>'; try { var resp = await signedPost('/devices', { roomId: currentRoom.roomId }); if (resp.status === 404){ revokedKick(); return; } if (!resp.ok){ body.innerHTML = '<div class="empty">Không tải được danh sách thiết bị.</div>'; return; } var data = await resp.json(); if (currentToken && !(data.devices || []).some(function(d){ return d.token === currentToken; })){ revokedKick(); return; } renderDevices(data.devices || [], data.events || []); } catch (e){ body.innerHTML = '<div class="empty">Lỗi tải thiết bị.</div>'; } }

async function revokeDevice(token, label){ if (!currentRoom || !token) return; if (!confirm('Huỷ ghép thiết bị "' + (label || 'này') + '"? Thiết bị sẽ không nhận giao dịch nữa.')) return; try { await signedPost('/revoke', { roomId: currentRoom.roomId, token: token }); } catch (e) {} if (currentToken && token === currentToken){ clearRoom(); currentRoom = null; txMap = new Map(); currentToken = null; renderPair(); return; } loadDevices(); }

async function unpairSelf(){ if (!confirm('Huỷ ghép máy này khỏi phòng? Bạn sẽ không nghe loa và không xem được lịch sử/báo cáo nữa.')) return; if (currentToken && currentRoom){ try { await signedPost('/revoke', { roomId: currentRoom.roomId, token: currentToken }); } catch (e) {} } clearRoom(); currentRoom = null; txMap = new Map(); currentToken = null; renderPair(); }

// ---- state + view switching ----
var currentRoom = loadRoom();
var currentToken = localStorage.getItem(TOKEN_KEY) || null;
var messaging = null;
var fbApp = null;
var listening = false;
var msgUnsub = null;
function getFbApp(){ if (!fbApp) fbApp = initializeApp(firebaseConfig); return fbApp; }
/** Reflect the live listen state on the "Bật loa" button (off → on/đang nghe). */
function updateEnableBtn(){ var b = $('enableBtn'); if (!b) return; b.textContent = listening ? '🔊 Đang nghe — bấm để tắt' : 'Bật loa (cho phép thông báo + âm thanh)'; if (b.classList) b.classList.toggle('listening', listening); }
/** Stop announcing on this tab (stays paired/registered; just detaches the live FCM listener). */
function stopListening(){ if (msgUnsub){ try { msgUnsub(); } catch (e) {} msgUnsub = null; } listening = false; updateEnableBtn(); status('Đã tắt loa. Bấm “Bật loa” để nghe lại.'); }
async function registerPush(){ if (!configured || !currentRoom) return false; try { if ('Notification' in window && Notification.permission === 'default') await Notification.requestPermission(); } catch (e) {} var reg = await navigator.serviceWorker.register('/firebase-messaging-sw.js'); await navigator.serviceWorker.ready; messaging = getMessaging(getFbApp()); var token = currentToken; if (!token){ token = await getToken(messaging, { vapidKey: vapidKey, serviceWorkerRegistration: reg }); } if (!token) return false; if (token !== currentToken){ var ok = await registerSpoke(currentRoom, token); if (!ok) return false; currentToken = token; localStorage.setItem(TOKEN_KEY, token); loadDevices(); } if (msgUnsub){ try { msgUnsub(); } catch (e) {} } msgUnsub = onMessage(messaging, function(payload){ handleBlob(payload && payload.data && payload.data.blob); }); listening = true; updateEnableBtn(); return true; }
var activeTab = 'loa';
var activePeriod = 'TODAY';
var historyLoading = false;

function renderActiveData(){ if (activeTab === 'history') renderHistory(); else if (activeTab === 'report') renderReport(); }

async function handleBlob(blob){ if (!blob || !currentRoom) return; var jsonStr = await decryptBlob(b64urlToBytes(currentRoom.roomSecret), blob); if (!jsonStr) return; var p; try { p = JSON.parse(jsonStr); } catch (e) { return; } if (!p || !p.txId) return; recordTx(p); renderActiveData(); if (seen.has(p.txId)) return; remember(p.txId); if (opts.incomeOnly && p.type !== 'in') { addToList(p); return; } speak(buildSentence(p), opts.repeat); addToList(p); }

async function registerSpoke(room, token){ var secretBytes = b64urlToBytes(room.roomSecret); var bodyObj = { roomId: room.roomId, macKey: await macKeyB64(secretBytes), token: token, label: deviceLabel() }; var bodyStr = JSON.stringify(bodyObj); var sig = await signBody(secretBytes, bodyStr); var url = String(room.senderUrl).replace(/\\/$/, '') + '/register'; var resp = await fetch(url, { method:'POST', headers:{ 'Content-Type':'application/json', 'X-Relay-Signature': sig }, body: bodyStr }); return resp.ok; }

// Pull the room's stored ciphertext from the relay (signed), decrypt every row on-device, and feed
// the transaction store. Pages newest-first via the 'before' cursor; capped so a huge room can't spin.
async function loadHistory(){ if (!currentRoom || historyLoading) return; historyLoading = true; var btn = $('refreshBtn'); if (btn) btn.textContent = 'Đang tải…'; try { var secretBytes = b64urlToBytes(currentRoom.roomSecret); var before = 0; var pages = 0; while (pages < 12){ var resp; try { resp = await signedPost('/history', { roomId: currentRoom.roomId, before: before, limit: 500 }); } catch (e) { break; } if (!resp.ok) break; var data = await resp.json(); var rows = (data && data.rows) || []; if (!rows.length) break; for (var i=0;i<rows.length;i++){ var jsonStr = await decryptBlob(secretBytes, rows[i].blob); if (!jsonStr) continue; var p; try { p = JSON.parse(jsonStr); } catch (e) { continue; } recordTx(p); } before = rows[rows.length - 1].ts; pages++; if (!data.more) break; } renderActiveData(); } catch (e) {} finally { historyLoading = false; var b2 = $('refreshBtn'); if (b2) b2.textContent = 'Tải lại dữ liệu'; } }

async function enable(){ if (!configured) { status('Máy chủ chưa cấu hình Firebase web — không nghe trực tiếp được (Lịch sử + Báo cáo vẫn xem được).'); return; } if (!currentRoom) { status('Chưa ghép phòng.'); return; } if (listening) { stopListening(); return; } try { speak('Loa loa', false); var ok = await registerPush(); status(ok ? 'Đang nghe. Giữ tab này mở.' : 'Không lấy được mã đẩy (push). Kiểm tra quyền thông báo.'); } catch (e) { status('Lỗi bật loa: ' + (e && e.message ? e.message : e)); } }

function setTabButtons(){ var tabs = document.querySelectorAll('.tab'); for (var i=0;i<tabs.length;i++){ tabs[i].classList.toggle('active', tabs[i].getAttribute('data-tab') === activeTab); } }
function switchTab(tab){ activeTab = tab; show('tab-loa', tab === 'loa'); show('tab-history', tab === 'history'); show('tab-report', tab === 'report'); show('tab-devices', tab === 'devices'); setTabButtons(); if (tab === 'devices') loadDevices(); else renderActiveData(); }
function setChipButtons(){ var chips = document.querySelectorAll('.chip'); for (var i=0;i<chips.length;i++){ chips[i].classList.toggle('active', chips[i].getAttribute('data-period') === activePeriod); } }
function switchPeriod(period){ activePeriod = period; setChipButtons(); renderReport(); }

function renderApp(){ show('pairView', false); show('appView', true); $('room').textContent = 'Phòng: ' + currentRoom.roomId; $('optShort').checked = opts.shortMessage; $('optRepeat').checked = opts.repeat; $('optIncome').checked = opts.incomeOnly; status(configured ? 'Đã ghép. Nhấn “Bật loa” để nghe trực tiếp; xem Lịch sử / Báo cáo / Thiết bị ở các tab trên.' : 'Đã ghép. Lịch sử + Báo cáo + Thiết bị hoạt động ngay; để nghe trực tiếp cần máy chủ bật Firebase web.'); switchTab('loa'); loadHistory(); updateEnableBtn(); if (currentToken && configured) registerPush().catch(function(){}); }
function renderPair(){ show('appView', false); show('pairView', true); status('Dán mã ghép từ máy shop để bắt đầu.'); }

async function tryPair(token){ var p = decodePairing(token); if (!p) { status('Mã ghép không hợp lệ.'); return; } if (!confirm('Ghép máy này vào phòng của shop?' + (currentRoom ? ' Phòng hiện tại sẽ bị thay thế.' : ''))) return; currentRoom = p; saveRoom(p); seen = new Set(); txMap = new Map(); localStorage.removeItem(TOKEN_KEY); currentToken = null; status('Đang ghép phòng…'); try { await registerSpoke(p, null); } catch (e) {} renderApp(); registerPush().then(function(ok){ if (ok) { status('Đã ghép. Máy này đã vào danh sách thiết bị.'); loadDevices(); } }).catch(function(){}); }

async function scanQr(){ if (!('BarcodeDetector' in window)) { status('Trình duyệt không hỗ trợ quét QR — hãy dán mã.'); return; } try { var stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } }); var video = $('cam'); show('cam', true); video.srcObject = stream; await video.play(); var detector = new BarcodeDetector({ formats: ['qr_code'] }); var timer = setInterval(async function(){ try { var codes = await detector.detect(video); if (codes && codes.length) { clearInterval(timer); stream.getTracks().forEach(function(t){ t.stop(); }); show('cam', false); tryPair(codes[0].rawValue); } } catch (e) {} }, 400); } catch (e) { status('Không mở được camera: ' + (e && e.message ? e.message : e)); } }

// wire up
if (!configured) show('cfgwarn', true);
if ('BarcodeDetector' in window) show('scanBtn', true);
$('pairBtn').addEventListener('click', function(){ tryPair($('tokenInput').value); });
$('scanBtn').addEventListener('click', scanQr);
$('enableBtn').addEventListener('click', enable);
$('testBtn').addEventListener('click', function(){ speak(buildSentence({ bank:'Vietcombank', amount:50000, type:'in', ts: Date.now() }), opts.repeat); });
$('unpairBtn').addEventListener('click', unpairSelf);
$('optShort').addEventListener('change', function(e){ opts.shortMessage = e.target.checked; saveOpts(opts); });
$('optRepeat').addEventListener('change', function(e){ opts.repeat = e.target.checked; saveOpts(opts); });
$('optIncome').addEventListener('change', function(e){ opts.incomeOnly = e.target.checked; saveOpts(opts); });
(function(){ var tabs = document.querySelectorAll('.tab'); for (var i=0;i<tabs.length;i++){ tabs[i].addEventListener('click', function(){ switchTab(this.getAttribute('data-tab')); }); } var chips = document.querySelectorAll('.chip'); for (var j=0;j<chips.length;j++){ chips[j].addEventListener('click', function(){ switchPeriod(this.getAttribute('data-period')); }); } var rb = $('refreshBtn'); if (rb) rb.addEventListener('click', loadHistory); var rh = $('refreshHistBtn'); if (rh) rh.addEventListener('click', loadHistory); var rd = $('refreshDevBtn'); if (rd) rd.addEventListener('click', loadDevices); })();

// pair from URL fragment if present (opened from the hub link)
if (location.hash && location.hash.length > 1) { tryPair(location.hash); } else if (currentRoom) { renderApp(); } else { renderPair(); }

// Detect remote revoke (hub kicked this device): re-check on tab focus + every 30s.
document.addEventListener('visibilitychange', function(){ if (!document.hidden) checkRevoked(); });
setInterval(checkRevoked, 30000);
`;
}

/** Firebase web config + VAPID key from env, as an ES module the app imports. Public, not secret. */
export function spokeConfigJs(env) {
  let cfg = "{}";
  if (env && env.FIREBASE_WEB_CONFIG) {
    // Already JSON; pass through (validated at deploy time by whoever set the var).
    cfg = String(env.FIREBASE_WEB_CONFIG);
  }
  const vapid = env && env.VAPID_PUBLIC_KEY ? String(env.VAPID_PUBLIC_KEY) : "";
  const configured = Boolean(env && env.FIREBASE_WEB_CONFIG && env.VAPID_PUBLIC_KEY);
  return "export const firebaseConfig = " + cfg + ";\n" +
    "export const vapidKey = " + JSON.stringify(vapid) + ";\n" +
    "export const configured = " + (configured ? "true" : "false") + ";\n";
}

/** Background FCM handler (classic service worker). Web can't autoplay audio off-tab → notify only. */
export function spokeSwJs(env) {
  const cfg = env && env.FIREBASE_WEB_CONFIG ? String(env.FIREBASE_WEB_CONFIG) : "{}";
  return "importScripts('https://www.gstatic.com/firebasejs/" + FIREBASE_VERSION + "/firebase-app-compat.js');\n" +
    "importScripts('https://www.gstatic.com/firebasejs/" + FIREBASE_VERSION + "/firebase-messaging-compat.js');\n" +
    "try {\n" +
    "  firebase.initializeApp(" + cfg + ");\n" +
    "  var messaging = firebase.messaging();\n" +
    "  messaging.onBackgroundMessage(function(){\n" +
    "    // No DOM/audio off-tab — show a notification so the employee opens the tab to hear it.\n" +
    "    self.registration.showNotification('Loa Loa Loa', { body: 'Giao dịch mới — mở tab để nghe loa.', tag: 'loaloaloa-tx', renotify: true });\n" +
    "  });\n" +
    "} catch (e) {}\n" +
    "self.addEventListener('notificationclick', function(event){\n" +
    "  event.notification.close();\n" +
    "  event.waitUntil(clients.matchAll({ type:'window', includeUncontrolled:true }).then(function(list){\n" +
    "    for (var i=0;i<list.length;i++){ if (list[i].url.indexOf('/web') !== -1) return list[i].focus(); }\n" +
    "    return clients.openWindow('/web');\n" +
    "  }));\n" +
    "});\n";
}

/** Minimal installable PWA manifest. */
export function spokeManifest() {
  return JSON.stringify({
    name: "Loa Loa Loa",
    short_name: "Loa Loa Loa",
    description: "Nghe thông báo giao dịch của shop trên trình duyệt.",
    start_url: "/web",
    scope: "/web/",
    display: "standalone",
    background_color: "#0f1115",
    theme_color: "#0f1115",
  });
}
