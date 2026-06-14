# Loa Loa Loa — Google Play Submission Pack

**App:** `com.loaloaloa` · targetSdk 35 · minSdk 26
**Account:** Personal, tạo sau 13/11/2023 → **bắt buộc closed test 12 testers / 14 ngày** trước production.
**Mục đích:** một nơi duy nhất chứa mọi nội dung cần điền/khai trong Play Console. Phần reviewer-facing viết tiếng Anh (Google reviewer đọc); phần hướng dẫn cho bạn viết tiếng Việt.

> Nguồn này **thay thế** mục "Data Safety draft" trong `docs/superpowers/specs/2026-06-03-play-store-release-design.md` (bản cũ nói sai "server không lưu giao dịch").

---

## 0. Đã sửa trước nộp (F1 + F2) — phải deploy worker

- **F1** `relay-worker/src/privacy.js` §4: khai báo relay lưu FCM token + **lịch sử giao dịch mã hoá (ciphertext) tối đa 210 ngày**, server không giải mã được.
- **F2** `relay-worker/src/worker.js` `handleClose`: đóng phòng giờ `DELETE FROM tx WHERE room=?` → wipe sạch server-side; `privacy.js` §7 sửa lời hứa xoá cho khớp.

⚠️ **Phải deploy lại worker** thì trang `/privacy` mới + hành vi xoá D1 mới có hiệu lực:
```
! cd relay-worker && npx wrangler deploy
```
Sau deploy: mở `https://loaloaloa.haveuever.workers.dev/privacy` xác nhận §4/§7 hiển thị bản mới.

---

## 1. Data safety form — đáp án Console (chốt)

**Does your app collect or share user data?** → **Yes**

| Data type | Collected | Shared | Optional? | Purpose | Encrypted in transit | User can delete |
|---|---|---|---|---|---|---|
| **Financial info → Other financial info** (nội dung giao dịch) | Yes | **No** | **Yes** (chỉ khi bật chia sẻ đa thiết bị) | App functionality | Yes | **Yes** |
| **Device or other IDs** (FCM token) | Yes | No | Yes | App functionality (push routing) | Yes | Yes |

- **Processed ephemerally?** Financial info → **No** (lưu D1 tới 210 ngày). Đừng tick "processed ephemerally".
- **Shared = No** cho cả hai: FCM/relay là hạ tầng vận chuyển ciphertext (service provider), không phải bên thứ ba đọc được → theo định nghĩa Play không tính là "sharing".
- **Data deletion available = Yes:** trong app *Huỷ ghép / Đóng phòng* (xoá toàn bộ server-side) + email liên hệ ở privacy policy.
- **KHÔNG khai:** Location, Contacts, Messages, Photos/Videos, Personal info (tên/email), App activity/Analytics → tất cả **N/A** (Microsoft Clarity đã gỡ).
- **Webhook / SePay (tuỳ chọn):** dữ liệu đi tới endpoint *của chính user* → user tự export, không phải chia sẻ bên thứ ba. Không khai riêng.

**Privacy policy URL:** `https://loaloaloa.haveuever.workers.dev/privacy`

---

## 2. Console declarations (text biện minh)

### 2a. Notification access (rủi ro reject cao nhất)
Trong **App content → Permissions declaration / Sensitive app permissions**, mục Notification listener:

> **Core functionality:** Loa Loa Loa is a "money-in" audio announcer for Vietnamese shop owners and street vendors. It reads incoming bank-transfer **notifications** posted by the user's banking apps, extracts the amount/direction/memo, and **announces them aloud via Text-to-Speech** so the seller hears each payment land without watching the screen. Notification access is the only Android API that exposes this content; there is no alternative API for it.
> **On-device:** parsing and announcing happen entirely on the device. Bank login credentials are never requested or stored.
> **Optional multi-device:** if the user enables it, the shop phone forwards each transaction **end-to-end encrypted (AES-256-GCM)** to paired employee phones via FCM; the relay server only moves ciphertext and cannot decrypt it.
> **Demo video:** [dán link YouTube unlisted — xem mục 3].

### 2b. Foreground service — special use (`FOREGROUND_SERVICE_SPECIAL_USE`)
Trong **App content → Foreground service permissions**:

> The single foreground service (`TransferNotificationListenerService`) runs while actively listening for and announcing incoming bank-transfer notifications in real time, including when the screen is off. No standard foreground-service type (location, media playback, data sync, etc.) describes notification-listening, so `specialUse` is the correct and only fit. Subtype is declared in the manifest `<property PROPERTY_SPECIAL_USE_FGS_SUBTYPE>`.

### 2c. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
> Real-time announcement must fire the instant a payment notification arrives, even on a dozing or OEM-aggressive device with the screen off. A Doze/battery-optimization exemption is required so the listener + wake-lock are not deferred; without it announcements arrive late or not at all. The app requests the exemption transparently and the user can decline.

### 2d. `CAMERA` + `RECEIVE_BOOT_COMPLETED` (rủi ro thấp)
> **CAMERA** — used solely to scan the pairing QR when linking an employee phone (`uses-feature ... required="false"`, so non-camera devices still install). No other use.
> **RECEIVE_BOOT_COMPLETED** — re-binds the notification listener after a reboot so announcements resume without the user reopening the app.

---

## 3. Demo video script (notification access — Play hay đòi)

Quay dọc ~45–60s, host YouTube **unlisted**, dán link vào mục 2a. Nói/hiện tiếng Việt cũng được nhưng nên có phụ đề/giọng giải thích.

1. **(0–8s)** Mở app, vào onboarding/permission. Cho thấy **màn prominent disclosure**: WHAT (đọc nội dung thông báo giao dịch từ app ngân hàng), WHY (đọc to + chuyển tiếp E2E tới máy nhân viên), processing on-device, link privacy policy.
2. **(8–18s)** Nhấn cấp quyền → màn hình hệ thống "Notification access" → bật cho Loa Loa Loa → quay lại app (đã sẵn sàng).
3. **(18–35s)** Kích một **thông báo chuyển khoản** thật/giả lập từ app ngân hàng (hoặc notification mẫu) → app **đọc to** số tiền + nội dung. Bật loa để nghe rõ trong video.
4. **(35–50s)** Mở tab Lịch sử cho thấy giao dịch vừa vào được ghi lại trên máy.
5. **(50–60s)** (tuỳ chọn) Cho thấy nút *Huỷ ghép / Đóng phòng* trong Cài đặt — minh hoạ user xoá dữ liệu.

Điểm mấu chốt reviewer cần thấy: **disclosure trước khi cấp quyền** + **notification access dùng đúng cho core feature đọc-to**.

---

## 4. App review / tester notes (reviewer-facing — paste tiếng Anh)

```
What the app does:
Loa Loa Loa announces incoming bank-transfer notifications aloud for shop owners.
It reads the notifications posted by the user's own banking apps, extracts the amount
and memo, and speaks them via Text-to-Speech so the seller hears each payment without
watching the screen. No bank login is ever requested.

How to use the core feature:
1. Open the app, complete onboarding, and grant Notification access when prompted
   (the screen explains what is read and why before asking).
2. Optionally pick which banking apps to listen to.
3. When a bank notification arrives, the app announces the amount and memo aloud and
   records it in History.

Account / login:
[x] No account required. Pairing employee phones uses a QR code + an end-to-end
    encryption key; there is no username/password and no server-side user account.

In-app purchases:
None. The app is free and open source.

Third-party content / services:
- Firebase Cloud Messaging (Google): transport only, for the optional multi-device
  feature; it moves AES-256-GCM ciphertext and cannot read transaction content.
- A Cloudflare Worker relay: a blind relay that stores only FCM tokens and encrypted
  ciphertext (cannot decrypt). Closing a room deletes all of it.
- Optional, user-configured: a webhook to the user's own endpoint, and the SePay API
  (user supplies their own credentials).

Permissions:
- Notification listener (BIND_NOTIFICATION_LISTENER_SERVICE): the core feature — reads
  bank-transfer notifications to announce them aloud. No alternative API exists.
- FOREGROUND_SERVICE_SPECIAL_USE: keeps the listener active (incl. screen-off) to
  announce in real time; no standard FGS type covers notification listening.
- REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: avoids Doze deferring real-time announcements.
- CAMERA: only to scan the pairing QR (optional feature).
- RECEIVE_BOOT_COMPLETED: re-binds the listener after reboot.

Anything non-obvious:
History is empty on first launch until a bank notification arrives. The multi-device
relay is inactive until the user pairs a second device. UI is in Vietnamese (target
market). A short demo video of the announce-aloud flow is provided above.
```

---

## 5. Closed test plan (12 testers / 14 ngày — bắt buộc)

1. **Internal testing** trước (instant, ≤100): upload AAB, verify cài từ Play + FCM chạy trên build **Play-signed**.
2. **Closed test:** mời **≥12 Google account thật trên máy thật**. Over-recruit **15–20** phòng rớt. Phải **opt-in đủ 14 ngày liên tục**.
   - Đẩy **1 bản update nhỏ giữa kỳ** (1 fix/tweak) → tín hiệu "test thật" giúp duyệt production.
   - Khuyến khích tester **mở app thật** (Play theo dõi engagement).
3. Sau 14 ngày, đủ ≥12 opt-in → **apply for production access** từ Dashboard, trả lời readiness questions.
4. **Production:** manual review cho notification access → có thể bị đòi video demo + back-and-forth. **Production cách hiện tại nhiều tuần.**

> Số 12 là policy hiện tại; **đọc lại con số chính xác trong Console** lúc setup (đã từng là 20).

---

## 6. Store listing & content rating

- **App name** (≤30): `Loa Loa Loa`
- **Category:** Finance. Nếu hiện "Financial features" declaration → chọn **none of these apply** (app chỉ *đọc thông báo*, không phải sản phẩm tài chính được quản lý: không cho vay/crypto/đầu tư/thanh toán).
- **Content rating (IARC):** không nội dung phản cảm → **Everyone / 3+**.
- Short desc (≤80) + full desc (≤4000) tiếng Việt: xem draft trong design doc §4 (có thể nhờ tôi viết hoàn chỉnh).

---

## 7. Submission hygiene checklist

- [ ] **Deploy worker** → `/privacy` bản mới live (`! cd relay-worker && npx wrangler deploy`).
- [ ] Privacy policy URL resolve được + khớp Data safety: `https://loaloaloa.haveuever.workers.dev/privacy`.
- [ ] Email liên hệ `lienhe@loaloaloa.app` **nhận mail thật** (đổi nếu chưa có hộp thư này).
- [ ] Data safety form điền theo mục 1, consistent với privacy policy + binary.
- [ ] Data deletion: in-app *Đóng phòng* + email (no account → không cần web URL xoá-tài-khoản riêng).
- [ ] Notification access declaration + demo video (mục 2a/3).
- [ ] FGS special-use + battery + camera/boot declarations (mục 2b–2d).
- [ ] Content rating xong; Finance "Financial features" = none apply.
- [ ] Build (AAB) Play-signed; version listing khớp build upload.
- [ ] targetSdk 35 đạt min (OK).
- [ ] Internal → Closed (12/14d) → Production.

---

## 8. I-do vs You-do (cập nhật)

| Việc | Trạng thái |
|---|---|
| F1/F2 code + privacy fixes | ✅ đã sửa (chờ commit + deploy) |
| Data safety / declarations / review notes / video script / closed-test plan | ✅ draft xong (doc này) |
| Deploy worker | ⏳ **bạn chạy** `npx wrangler deploy` (cần Cloudflare auth) |
| Quay video demo | ⏳ bạn quay theo script mục 3 |
| Điền Console (Data safety, declarations, rating, listing, privacy URL) | ⏳ bạn làm trong Console |
| Gom 12–20 tester | ⏳ bạn |
| Confirm email `lienhe@loaloaloa.app` hoạt động | ⏳ bạn |
