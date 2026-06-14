# Play publish automation (Gradle Play Publisher)

Tự động **upload AAB + store listing** lên một Play track qua [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) (GPP). Dùng từ **release thứ 2 trở đi** và để promote build giữa các track.

## Cái này KHÔNG làm được (Google không mở API)
- ❌ Data safety form · ❌ Content rating (IARC) · ❌ Permission declarations (notification access, FGS…) · ❌ Privacy policy URL.

Tất cả mục trên vẫn phải làm tay trong Console — xem `docs/play-submission-pack.md`. GPP chỉ chạm: listing text/graphics, release notes, contact info, và upload AAB lên track.

## Phụ thuộc đã wiring sẵn (trong repo)
- Plugin: `com.github.triplet.play` 3.12.1 — `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` (`play {}` block).
- Listing-as-code: `app/src/main/play/` (title / short / full description / graphics / release-notes, locale `vi`).
- Workflow: `.github/workflows/play-publish.yml` (manual `workflow_dispatch`).
- `play {}` mặc định: track `internal`, `releaseStatus = DRAFT`, `resolutionStrategy = AUTO` (versionCode tự tăng cao hơn max trên Play), AAB (không APK).

## Set up một lần

### 1. Service account (Google Cloud)
1. [Google Cloud Console](https://console.cloud.google.com) → tạo (hoặc chọn) project.
2. **APIs & Services → Enable APIs** → bật **Google Play Android Developer API**.
3. **IAM & Admin → Service Accounts → Create** → tạo SA (vd `play-publisher`). Không cần cấp role GCP.
4. Vào SA → **Keys → Add key → JSON** → tải file JSON (đây là bí mật, đừng commit).

### 2. Liên kết SA trong Play Console
1. [Play Console](https://play.google.com/console) → **Users and permissions → Invite new users**.
2. Mời **email của service account** (dạng `...@...iam.gserviceaccount.com`).
3. App permissions cho app Loa Loa Loa: tối thiểu **Release to testing tracks** + **Manage store presence** (nếu muốn push listing). Có thể siết chỉ track internal/closed lúc đầu.

### 3. GitHub secrets
Repo → Settings → Secrets and variables → Actions. Cần (một số đã có sẵn từ `release.yml`):

| Secret | Có sẵn? | Nội dung |
|---|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | **mới** | **base64** của file SA JSON: `base64 -i play-sa.json \| pbcopy` |
| `UPLOAD_KEYSTORE_B64` | đã có | base64 của upload keystore |
| `UPLOAD_KEYSTORE_PASSWORD` / `UPLOAD_KEY_ALIAS` / `UPLOAD_KEY_PASSWORD` | đã có | mật khẩu/alias key |
| `GOOGLE_SERVICES_JSON` | đã có | base64 google-services.json |

## Chạy
**Lần đầu của app mới:** vẫn phải tạo app + upload AAB đầu tiên + điền mọi form policy **bằng tay** trong Console. API không tạo app được.

**Sau đó:** GitHub → tab **Actions → Publish to Play → Run workflow**, chọn:
- `track`: internal / closed / production
- `release_status`: `draft` (mặc định, an toàn — vào Console bấm live) hoặc `completed` (đẩy thẳng tới track)
- `publish_listing`: bật nếu muốn ghi đè listing trên Console bằng `app/src/main/play/` ⚠️

**Local (tuỳ chọn):**
```bash
export PLAY_SERVICE_ACCOUNT_JSON_FILE=/đường/dẫn/play-sa.json
export UPLOAD_KEYSTORE_PATH=... UPLOAD_KEYSTORE_PASSWORD=... UPLOAD_KEY_ALIAS=... UPLOAD_KEY_PASSWORD=...
./gradlew publishReleaseBundle --track internal --release-status draft
./gradlew publishReleaseListing        # chỉ khi muốn đẩy listing
```

## Lưu ý
- **`publish_listing` ghi đè** title/desc/graphics trên Console bằng file trong repo → coi `app/src/main/play/` là nguồn chân lý nếu bật. Mặc định tắt.
- **Title** đang để `Loa Loa Loa`. Muốn dùng bản ASO `Loa Loa Loa: Báo Chuyển Khoản` thì sửa `app/src/main/play/listings/vi/title.txt` (≤30 ký tự).
- **DRAFT** không tới tay tester tới khi bạn set live trong Console. Internal test muốn nhận ngay thì chạy `release_status = completed`.
- `versionName` trong workflow lấy từ `github.ref_name` — nên chạy workflow trên một tag `vX.Y.Z` để versionName đẹp; versionCode thì AUTO lo.
- Không đụng `release.yml` (semantic-release + GitHub Release) — hai luồng độc lập.
