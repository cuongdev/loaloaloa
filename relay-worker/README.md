# Loa Loa Loa relay Sender (Cloudflare Worker + FCM)

This is the **Sender** for Loa Loa Loa's device relay: a tiny Cloudflare Worker that lets a shop's
**hub** phone (the one logged into the bank / SePay) forward each detected transaction to its
employees' **spoke** phones, which announce it on the loa **with no bank login on the employee
phone**.

```
HUB (has bank)  ──encrypt+sign──▶  Sender (this Worker + KV)  ──FCM──▶  SPOKE phones (no bank) ──▶ loa
```

The Worker is a **blind relay**. The transaction (amount + content) is AES-256-GCM encrypted
end-to-end on the hub, so the Worker and Google only ever move ciphertext. Requests are
HMAC-SHA256 signed with each room's MAC subkey — which is independent of the encryption key, so the
Worker can authenticate senders but can never decrypt a payload.

> **The app builds and runs without any of this.** Until you add `google-services.json` (Step 1.3)
> the relay is simply inactive — the app compiles, installs, and works as the single-device app it
> is today. Set this up only when you want hub→spoke sharing.

---

## What you need (one-time)

- A **Firebase** project (free — no Blaze plan needed; FCM messaging is free).
- A **Cloudflare** account (free tier is ample for one shop).
- Node.js 18+ locally (to run `wrangler`).

Total time: ~15 minutes.

---

## Step 1 — Firebase (FCM)

1. **Create a project** at <https://console.firebase.google.com> (or reuse one).

2. **Add an Android app** to it: *Project settings → General → Your apps → Add app → Android*.
   - Android package name: **`com.loaloaloa`** (must match exactly).
   - Nickname / SHA-1 are optional for FCM.

3. **Download `google-services.json`** and drop it into the repo at:
   ```
   app/google-services.json
   ```
   This file flips on the `com.google.gms.google-services` Gradle plugin (applied conditionally —
   see `app/build.gradle.kts`) and activates FCM in the app. It is git-ignored (per-shop config).
   Rebuild/reinstall the app after adding it:
   ```
   ./gradlew :app:installDebug
   ```

4. **Create a service-account key for the Sender.** In the Firebase console:
   *Project settings → Service accounts → Generate new private key → Generate key*.
   A `*.json` file downloads. **This is a real secret** — it lets the Sender call FCM on your
   project's behalf. Never commit it, never put it in the APK. You'll paste it into Cloudflare in
   Step 2.5.

---

## Step 2 — Cloudflare Worker

All commands run from this `relay-worker/` directory.

1. **Install dependencies** (gets `wrangler`, Cloudflare's CLI):
   ```
   npm install
   ```

2. **Log in** to Cloudflare:
   ```
   npx wrangler login
   ```

3. **Create the KV namespace** and paste its id into `wrangler.toml`:
   ```
   npx wrangler kv namespace create RELAY_KV
   ```
   Copy the printed `id = "…"` into the `[[kv_namespaces]]` block in `wrangler.toml`
   (replacing `REPLACE_WITH_KV_NAMESPACE_ID`).

4. **Create the D1 database** (holds the encrypted history the web dashboard reads back — Step 4) and
   run its migration:
   ```
   npx wrangler d1 create tingting-relay-history
   npx wrangler d1 execute tingting-relay-history --remote --file migrations/0001_history.sql
   ```
   Copy the printed `database_id = "…"` into the `[[d1_databases]]` block in `wrangler.toml`. D1 is
   optional — without it the relay still relays live; you just lose the browser history/reports.

5. **Store the Firebase service account as a secret** (the file from Step 1.4):
   ```
   npx wrangler secret put FCM_SERVICE_ACCOUNT
   ```
   When prompted, paste the **entire contents** of the service-account `.json` (one paste, then
   Enter). It is stored encrypted by Cloudflare and is never written to the repo.

6. **Deploy:**
   ```
   npx wrangler deploy
   ```
   Note the URL it prints, e.g. `https://loaloaloa-relay.<your-subdomain>.workers.dev`.
   That URL is your **Sender URL**.

7. **Smoke-test** the deploy:
   ```
   curl https://loaloaloa-relay.<your-subdomain>.workers.dev/
   # → loaloaloa relay sender: ok
   ```

8. **(Optional) Install-from-QR for employees without the app.** The pairing QR is the link
   `https://<your-sender>/pair#<token>`. An employee who scans it with their normal camera lands on
   the Worker's `GET /pair` page, which offers the APK; once installed (and this host verified), the
   same link opens the app straight into pairing. To enable it:
   - **Host the APK** somewhere public and point the Worker at it:
     ```
     npx wrangler secret put APK_URL     # or add it under [vars] in wrangler.toml
     # value = a direct download URL to the signed APK, e.g. a GitHub release asset
     ```
   - **Verify the App Link.** The Worker serves `/.well-known/assetlinks.json` with the app's signing
     SHA-256. The committed value is the **debug** keystore's fingerprint; before shipping a signed
     build, add your **release** SHA-256 to `ANDROID_CERT_SHA256` in `src/worker.js` and redeploy.
     Get a fingerprint with:
     ```
     keytool -list -v -keystore <your.keystore> -alias <alias> | grep SHA256
     ```
   - Without `APK_URL`, `/download` returns a "chưa cấu hình" notice and the rest of the relay still
     works — employees just install the app manually, then scan the QR in-app.

---

## Step 3 — Pair the phones

1. **Hub** (shop phone, has the bank app): open Loa Loa Loa →
   *Settings → "Chia sẻ thông báo (loa nhân viên)"* → paste the **Sender URL** from Step 2.6 →
   **"Tạo phòng & hiện mã QR"**. A QR appears.

2. **Spoke** (each employee phone, no bank): install the app → *Settings → relay* → **"Quét mã QR"**
   → scan the hub's QR. The phone joins as a spoke and registers its FCM token with the Sender
   automatically. An employee who **doesn't have the app yet** can scan the same QR with their normal
   camera: it opens the Sender's `/pair` page, which offers the APK (Step 2.8); after installing, the
   link opens the app straight into pairing.

3. On each **spoke**, complete the usual reliability onboarding so FCM wakeups always announce:
   grant notification permission and exempt the app from battery optimization (the app already
   prompts for these). This matters most on Xiaomi/Oppo/Vivo/Realme/Samsung.

That's it. When the hub detects a transaction it encrypts it, signs it, and POSTs to the Sender,
which fans it out to every paired spoke as a high-priority FCM data message — and the spokes
announce it through the exact same pipeline (TTS, quiet hours, min-amount, daily-total) as a
locally-detected transaction.

---

## Step 4 — (Optional) Web spoke (hear it in a browser, no app)

A counter PC or any browser can act as a spoke at **`https://<your-sender>/web`** — it joins a room
by pairing token, registers an **FCM web push token** (just another token in the room — the Sender
fans out to it unchanged), decrypts the same blob on-device, and reads it aloud with the Web Speech
API. The hub still must be Android (it reads bank notifications). The layout is responsive: a single
phone-width column on a phone, widening to a full desktop dashboard (report sections tile 2-up) on a
wide screen.

The page has four tabs: **Loa** (live announce + settings), **Lịch sử** (full transaction history),
**Báo cáo** (income reports — growth vs yesterday/last-week/last-month, daily/monthly revenue
charts, peak-hour and busiest-weekday insights, per-bank breakdown, records), and **Thiết bị**
(paired-device list with a per-device **"Huỷ ghép"** revoke button — each asks for confirmation — plus
a pair/unpair audit log built from the relay's `events[]`). History and reports are the same ones the
app shows: the Sender logs every transaction's ciphertext to **D1** (Step 2.4), and the browser pulls
those rows back via the signed `POST /history`, decrypts each on-device, and rebuilds every list and
chart client-side — the Sender stays end-to-end blind (it stores only ciphertext + a hash + a receipt
timestamp, never plaintext). Because history, reports, and device management need only the room secret
plus `/history` + `/devices`, **those tabs work even before the Firebase web vars below are set** —
only the live "Bật loa" push needs them. To enable live announce:

1. **Add a Web app** in the Firebase console for this same project
   (`tingting-relay-16ea42`): *Project settings → General → Your apps → Add app → Web*. Copy the
   `firebaseConfig` object it shows.

2. **Generate a Web Push key:** *Project settings → Cloud Messaging → Web configuration → Web Push
   certificates → Generate key pair*. Copy the public key string.

3. **Set the two Worker vars** (public, not secrets) — uncomment the `[vars]` block in `wrangler.toml`
   and paste both, then `npx wrangler deploy`:
   ```toml
   [vars]
   FIREBASE_WEB_CONFIG = '{"apiKey":"…","authDomain":"…","projectId":"tingting-relay-16ea42","messagingSenderId":"221042113822","appId":"1:221042113822:web:…"}'
   VAPID_PUBLIC_KEY = "B…"
   ```

4. **Use it:** open `https://<your-sender>/web`, paste the hub's pairing code (or scan its QR — the
   `/pair` page also shows a **"Nghe trên trình duyệt"** button that opens `/web` with the token),
   then tap **"Bật loa"** to grant notifications + unlock audio. Keep the tab open.

**Limits (web, not app):** audio plays only while the tab is open and after that first tap; a closed
or sleeping browser can't be woken to speak, so a backgrounded push shows a notification instead.
Best on an always-open tab on a counter PC. iOS Safari is weakest (web push needs an installed PWA on
iOS 16.4+, and off-tab audio is effectively impossible). For reliable hands-off announcing, use the
Android spoke. These limits are about **live audio only** — the Lịch sử and Báo cáo tabs just pull +
decrypt stored history, so they work in any browser regardless. The pairing token rides in the URL
fragment here too, so the Sender never sees it.

---

## How delivery behaves

- **High-priority, data-only** FCM messages wake a dozing/killed spoke and show **no** tray
  notification.
- **Short TTL (180s):** a push that can't be delivered promptly **expires** rather than arriving
  late and being announced stale. Spokes never replay a backlog on reconnect — by design.
- **At-least-once:** if FCM hits a transient error for some spokes, the Sender returns `503` and the
  hub retries (WorkManager). Spokes dedupe repeats by `txId`, so a re-delivery is never spoken
  twice.
- **Self-pruning:** when FCM reports a token as unregistered (app uninstalled / token expired) the
  Sender drops it from the room automatically.

---

## Endpoint reference (for debugging)

All are `POST` with a JSON body and an `X-Relay-Signature: <base64url HMAC-SHA256(body, macKey)>`
header, except the health check. `macKey` is the room's MAC subkey (base64url), derived on-device
from the room secret.

| Endpoint | Body | Purpose |
|---|---|---|
| `GET /` | — | Health check → `loaloaloa relay sender: ok` |
| `GET /pair` | — | HTML install/pairing landing (see Step 2.8). Token rides in the `#fragment`, never sent to the server. |
| `GET /download` | — | 302 → the APK (`APK_URL` var); the landing page's download button. |
| `GET /.well-known/assetlinks.json` | — | Digital Asset Links so Android verifies this host and opens `/pair` in the app. |
| `GET /web` (+ `/web/app.js`, `/web/config.js`, `/web/manifest.webmanifest`, `/firebase-messaging-sw.js`) | — | Web-spoke PWA + dashboard: hear a room's transactions in a browser, no app (Step 4). Registers an FCM **web** token; decrypts the same blob on-device. |
| `POST /register` | `{roomId, macKey, token?, label?}` | Hub provisions a room's macKey (trust-on-first-use, pinned); spoke (app **or** web) adds its FCM `token` (+ optional device `label`) and logs a `pair` event. |
| `POST /send` | `{roomId, blob}` | Hub relays one encrypted transaction; fanned out to the room's tokens **and** logged to D1 (ciphertext only) for the web dashboard. |
| `POST /history` | `{roomId, before?, limit?}` | Web dashboard pulls a page of the room's stored ciphertext rows, newest first (`before` = epoch-ms cursor, exclusive; `limit` 1..1000, default 500). Returns `{ok, rows:[{blob, ts}], more}`. |
| `POST /devices` | `{roomId}` | List the room's paired devices + the pair/unpair audit log (the "Thiết bị" tab). Returns `{ok, devices:[{token, label, ts}], events:[{type, token, label, ts}]}`, both newest-first. |
| `POST /revoke` | `{roomId, token}` | Deliberately unpair one spoke token from a room; logs an `unpair` event. Returns `{ok, removed}`. |

Responses: `200` on success; `400` bad body; `401` bad signature; `404` unknown room; `409` a
different macKey was presented for an existing room; `503` retry-after-transient-FCM-failure.

KV layout — one entry per room:
```
room:{roomId} -> {
  "macKey": "<base64url>",
  "devices": [ { "token": "<fcm token>", "label": "<device name>", "ts": <epoch-sec> }, ... ],
  "events":  [ { "type": "pair"|"unpair", "token": "...", "label": "...", "ts": <epoch-sec> }, ... ]
}
```
`events` is the pair/unpair audit log (capped at the newest 100). A `/send` to a dead FCM token
auto-prunes it from `devices` silently — that is not a deliberate unpair, so no event is logged.

D1 layout — one row per transaction (encrypted; feeds the web dashboard):
```
tx ( room TEXT, hash TEXT = SHA-256(blob), blob TEXT = ciphertext, ts INTEGER = receipt epoch-ms )
PRIMARY KEY (room, hash)   -- a hub retry resends the identical blob → same hash → INSERT OR IGNORE
```
Rows older than ~210 days are pruned on each write. The relay never stores plaintext.

Watch live logs while testing:
```
npx wrangler tail
```

---

## Security notes

- **End-to-end:** the Worker and Google see only ciphertext (`blob`). The service-account credential
  can authenticate FCM sends but cannot decrypt a payload — the AES key never leaves paired devices.
- **The service account is the only real secret.** It lives solely as the `FCM_SERVICE_ACCOUNT`
  Worker secret — never in git, never in the APK.
- **The room secret** never leaves the paired devices except inside the pairing QR, which is shown
  transiently on the hub and scanned locally.
- **Revoking one device:** the web "Thiết bị" tab (or `POST /revoke`) unpairs a single spoke token
  and logs an `unpair` event, leaving every other paired device untouched. Use this for "an employee
  left" without disrupting the rest.
- **Rotating / resetting a room:** on the hub, "Huỷ phòng" then "Tạo phòng" again generates a fresh
  room secret. That orphans every previously paired spoke (they can no longer decrypt) — which is
  also the intended "reset everything / revoke all" escape hatch. Re-scan the new QR to re-pair.

---

## Costs / limits

Cloudflare Workers free tier (100k requests/day), KV free tier, and D1 free tier (5 GB, 100k
row-writes/day, 5M row-reads/day) are far more than a single shop's transaction volume needs — D1 is
what makes the full web dashboard cost nothing. FCM messaging is free at any volume. No Firebase Blaze
plan, no Cloud Functions.
