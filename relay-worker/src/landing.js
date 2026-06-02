/**
 * Marketing landing page served at GET / (the relay's root).
 *
 * Self-contained: inline CSS + a sliver of JS (year stamp + smooth scroll). Screenshots are pulled
 * from GitHub raw on the default branch, so the repo's docs/screenshots/*.png must be pushed for the
 * gallery to render. Brand palette matches the app (teal #0EA5A4 / mint #5EEAD4) and the in-app
 * screenshots are light, so the page is a light theme to stay cohesive.
 */

const REPO = "https://github.com/cuongdev/loaloaloa";
const SHOTS = "https://raw.githubusercontent.com/cuongdev/loaloaloa/main/docs/screenshots";
const WEB_DEMO = "/web";
const APK = "/download"; // Worker 302 -> env.APK_URL (the signed APK)

const LOGO_SVG = `<svg width="44" height="44" viewBox="0 0 64 64" role="img" aria-label="Loa Loa Loa"><g fill="#0EA5A4"><path d="M6 26h8v12H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z"/><path d="M14 24 42 14v36L14 40z"/><path d="M20 40h6v9a3 3 0 0 1-6 0z"/></g><path d="M48 24q6 8 0 16" fill="none" stroke="#0EA5A4" stroke-width="3.5" stroke-linecap="round"/><path d="M54 19q10 13 0 26" fill="none" stroke="#5EEAD4" stroke-width="3.5" stroke-linecap="round"/></svg>`;

export function landingPage() {
  return `<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
<title>Loa Loa Loa — Nghe rõ tiền chuyển khoản về, ngay khi nó tới</title>
<meta name="description" content="Cái loa 'Tiền về!' rảnh tay cho quán Việt. Thông báo ngân hàng vừa tới là đọc to số tiền bằng tiếng Việt — không cần đăng nhập ngân hàng, không cần máy POS. Android, mã nguồn mở." />
<meta name="theme-color" content="#0EA5A4" />
<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Cg fill='%230EA5A4'%3E%3Cpath d='M6 26h8v12H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z'/%3E%3Cpath d='M14 24 42 14v36L14 40z'/%3E%3Cpath d='M20 40h6v9a3 3 0 0 1-6 0z'/%3E%3C/g%3E%3C/svg%3E" />
<meta property="og:type" content="website" />
<meta property="og:title" content="Loa Loa Loa — Nghe rõ tiền chuyển khoản về, ngay khi nó tới" />
<meta property="og:description" content="Cái loa 'Tiền về!' rảnh tay cho quán Việt. Đọc to số tiền bằng tiếng Việt ngay khi ngân hàng báo về." />
<meta property="og:image" content="${SHOTS}/01-home.png" />
<meta name="twitter:card" content="summary_large_image" />
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  :root{
    --teal:#0EA5A4; --teal-d:#0b7d7c; --mint:#5EEAD4; --ink:#0f2e2d; --muted:#5b6b6a;
    --bg:#f3faf9; --card:#ffffff; --line:#e2efed; --green:#16a34a; --red:#dc2626;
  }
  html{scroll-behavior:smooth}
  body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",sans-serif;
       color:var(--ink); background:var(--bg); line-height:1.6; -webkit-font-smoothing:antialiased}
  a{color:inherit}
  .wrap{max-width:1080px; margin:0 auto; padding:0 22px}
  .btn{display:inline-flex; align-items:center; gap:8px; padding:14px 22px; border-radius:14px;
       font-size:16px; font-weight:700; text-decoration:none; border:0; cursor:pointer; transition:transform .12s ease, box-shadow .12s ease}
  .btn:hover{transform:translateY(-2px)}
  .btn-primary{background:var(--teal); color:#fff; box-shadow:0 10px 24px rgba(14,165,164,.32)}
  .btn-ghost{background:#fff; color:var(--teal-d); border:1.5px solid var(--line)}

  /* nav */
  nav{position:sticky; top:0; z-index:20; backdrop-filter:saturate(180%) blur(12px);
      background:rgba(243,250,249,.82); border-bottom:1px solid var(--line)}
  nav .wrap{display:flex; align-items:center; justify-content:space-between; height:64px}
  .brand{display:flex; align-items:center; gap:10px; font-weight:800; font-size:18px}
  .nav-links{display:flex; align-items:center; gap:22px; font-size:15px; font-weight:600}
  .nav-links a{text-decoration:none; color:var(--muted)}
  .nav-links a:hover{color:var(--ink)}
  .nav-cta{padding:9px 16px; font-size:14px}
  @media(max-width:720px){.nav-links a:not(.nav-cta){display:none}}

  /* hero */
  .hero{position:relative; overflow:hidden;
        background:radial-gradient(1200px 500px at 70% -10%, rgba(94,234,212,.35), transparent 60%),
                   linear-gradient(180deg,#eafffb 0%, var(--bg) 70%)}
  .hero .wrap{display:grid; grid-template-columns:1.05fr .95fr; gap:40px; align-items:center;
              padding:72px 22px 64px}
  .pill{display:inline-flex; align-items:center; gap:8px; background:#fff; border:1px solid var(--line);
        color:var(--teal-d); font-weight:700; font-size:13px; padding:7px 14px; border-radius:999px; margin-bottom:20px}
  .dot{width:8px;height:8px;border-radius:50%;background:var(--green);box-shadow:0 0 0 0 rgba(22,163,74,.5);animation:pulse 1.8s infinite}
  @keyframes pulse{0%{box-shadow:0 0 0 0 rgba(22,163,74,.5)}70%{box-shadow:0 0 0 10px rgba(22,163,74,0)}100%{box-shadow:0 0 0 0 rgba(22,163,74,0)}}
  h1{font-size:48px; line-height:1.08; letter-spacing:-.02em; font-weight:850}
  h1 .hl{color:var(--teal)}
  .lead{font-size:19px; color:var(--muted); margin:18px 0 28px; max-width:34ch}
  .cta-row{display:flex; gap:14px; flex-wrap:wrap}
  .trust{margin-top:22px; color:var(--muted); font-size:14px; display:flex; gap:18px; flex-wrap:wrap}
  .trust b{color:var(--ink)}
  @media(max-width:860px){.hero .wrap{grid-template-columns:1fr; text-align:center}
    h1{font-size:38px} .lead{margin-left:auto;margin-right:auto} .cta-row,.trust{justify-content:center}}

  /* phone mockup */
  .phone{justify-self:center; width:300px; max-width:78vw; position:relative}
  .phone .frame{border:11px solid #0f2e2d; border-radius:42px; overflow:hidden; background:#fff;
        box-shadow:0 34px 70px rgba(15,46,45,.28)}
  .phone img{display:block; width:100%}
  .toast{position:absolute; left:-26px; top:96px; background:#fff; border:1px solid var(--line);
         border-radius:16px; padding:12px 14px; width:230px; box-shadow:0 18px 40px rgba(15,46,45,.18);
         display:flex; gap:11px; align-items:center; animation:float 3.4s ease-in-out infinite}
  @media(max-width:520px){.toast{display:none}}
  @keyframes float{0%,100%{transform:translateY(0)}50%{transform:translateY(-9px)}}
  .toast .ic{width:38px;height:38px;border-radius:11px;background:#e9fbf6;display:grid;place-items:center;font-size:20px;flex:0 0 auto}
  .toast .am{font-weight:800;color:var(--green);font-size:16px;line-height:1.2}
  .toast .sub{font-size:12px;color:var(--muted)}
  .wave{display:inline-flex;gap:3px;align-items:flex-end;height:16px;margin-left:auto}
  .wave i{width:3px;background:var(--teal);border-radius:2px;animation:eq .9s ease-in-out infinite}
  .wave i:nth-child(2){animation-delay:.15s}.wave i:nth-child(3){animation-delay:.3s}.wave i:nth-child(4){animation-delay:.45s}
  @keyframes eq{0%,100%{height:5px}50%{height:16px}}

  /* sections */
  section{padding:72px 0}
  .eyebrow{color:var(--teal-d); font-weight:800; letter-spacing:.04em; text-transform:uppercase; font-size:13px; text-align:center}
  h2{font-size:34px; line-height:1.15; letter-spacing:-.02em; text-align:center; margin:10px 0 8px; font-weight:850}
  .sec-lead{text-align:center; color:var(--muted); font-size:17px; max-width:60ch; margin:0 auto 44px}

  .quote{background:linear-gradient(135deg,#0b7d7c,#0EA5A4); color:#fff; border-radius:24px; padding:44px;
         text-align:center; box-shadow:0 24px 60px rgba(14,165,164,.28)}
  .quote .big{font-size:30px; font-weight:800; line-height:1.25}
  .quote p{color:#d6fffb; font-size:17px; margin-top:14px; max-width:62ch; margin-left:auto; margin-right:auto}

  .grid{display:grid; grid-template-columns:repeat(3,1fr); gap:18px}
  @media(max-width:860px){.grid{grid-template-columns:1fr 1fr}}
  @media(max-width:560px){.grid{grid-template-columns:1fr}}
  .card{background:var(--card); border:1px solid var(--line); border-radius:18px; padding:24px;
        transition:transform .14s ease, box-shadow .14s ease}
  .card:hover{transform:translateY(-4px); box-shadow:0 18px 40px rgba(15,46,45,.1)}
  .card .emo{font-size:26px} .card h3{font-size:18px; margin:12px 0 6px} .card p{color:var(--muted); font-size:15px}

  .steps{display:grid; grid-template-columns:repeat(4,1fr); gap:18px; counter-reset:s}
  @media(max-width:860px){.steps{grid-template-columns:1fr 1fr}}
  @media(max-width:480px){.steps{grid-template-columns:1fr}}
  .step{background:#fff;border:1px solid var(--line);border-radius:18px;padding:24px;position:relative}
  .step::before{counter-increment:s;content:counter(s);position:absolute;top:-14px;left:22px;width:34px;height:34px;
    background:var(--teal);color:#fff;border-radius:10px;display:grid;place-items:center;font-weight:800;box-shadow:0 8px 18px rgba(14,165,164,.4)}
  .step h3{margin:10px 0 6px;font-size:17px}.step p{color:var(--muted);font-size:14px}

  .shots{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}
  @media(max-width:860px){.shots{grid-template-columns:1fr 1fr}}
  @media(max-width:520px){.shots{grid-template-columns:1fr 1fr}}
  .shot{border:1px solid var(--line);border-radius:18px;overflow:hidden;background:#fff;box-shadow:0 12px 30px rgba(15,46,45,.08)}
  .shot img{display:block;width:100%}
  .shot .cap{padding:12px 14px;font-size:13px;color:var(--muted);text-align:center;border-top:1px solid var(--line)}

  .secure{display:grid;grid-template-columns:repeat(2,1fr);gap:18px;max-width:820px;margin:0 auto}
  @media(max-width:640px){.secure{grid-template-columns:1fr}}
  .sec-item{display:flex;gap:14px;background:#fff;border:1px solid var(--line);border-radius:16px;padding:20px}
  .sec-item .emo{font-size:24px}.sec-item b{display:block;margin-bottom:4px}.sec-item span{color:var(--muted);font-size:14.5px}

  .final{background:linear-gradient(135deg,#0b7d7c,#0EA5A4);border-radius:28px;padding:56px 32px;text-align:center;color:#fff;
         box-shadow:0 28px 70px rgba(14,165,164,.32)}
  .final h2{color:#fff}.final p{color:#d6fffb;font-size:18px;margin:6px auto 28px;max-width:50ch}
  .final .btn-primary{background:#fff;color:var(--teal-d)}
  .final .btn-ghost{background:transparent;color:#fff;border-color:rgba(255,255,255,.5)}

  footer{padding:40px 0 56px;color:var(--muted);font-size:14px;text-align:center;border-top:1px solid var(--line);margin-top:24px}
  footer a{color:var(--teal-d);text-decoration:none;font-weight:600}
</style>
</head>
<body>

<nav><div class="wrap">
  <div class="brand">${LOGO_SVG}<span>Loa Loa Loa</span></div>
  <div class="nav-links">
    <a href="#features">Tính năng</a>
    <a href="#how">Cách hoạt động</a>
    <a href="${REPO}" target="_blank" rel="noopener">GitHub</a>
    <a class="btn btn-primary nav-cta" href="${APK}">Tải APK</a>
  </div>
</div></nav>

<header class="hero"><div class="wrap">
  <div>
    <span class="pill"><span class="dot"></span> Đang chạy · miễn phí · mã nguồn mở</span>
    <h1>Nghe rõ <span class="hl">tiền chuyển khoản</span> về — ngay khi nó tới.</h1>
    <p class="lead">Cái loa “Tiền về!” rảnh tay cho mọi quán xá Việt Nam. Ngân hàng vừa báo là điện thoại đọc to số tiền bằng tiếng Việt — không cần đăng nhập ngân hàng, không cần máy POS.</p>
    <div class="cta-row">
      <a class="btn btn-primary" href="${APK}">⬇️ Tải APK</a>
      <a class="btn btn-ghost" href="${WEB_DEMO}">🌐 Thử ngay trên web</a>
    </div>
    <div class="trust">
      <span><b>Android 8.0+</b></span>
      <span>🔒 <b>Riêng tư</b> trên máy</span>
      <span>📡 <b>E2E</b> đa thiết bị</span>
    </div>
  </div>
  <div class="phone">
    <div class="frame"><img src="${SHOTS}/01-home.png" alt="Màn hình Loa Loa Loa" loading="eager" /></div>
    <div class="toast">
      <div class="ic">📣</div>
      <div>
        <div class="am">+500.000 đ</div>
        <div class="sub">“Tiền vào năm trăm nghìn”</div>
      </div>
      <span class="wave"><i></i><i></i><i></i><i></i></span>
    </div>
  </div>
</div></header>

<section id="problem"><div class="wrap">
  <div class="quote">
    <div class="big">💸 “Chuyển rồi anh ơi!”</div>
    <p>Người bán tay đang gói hàng, mắt không rảnh nhìn điện thoại, tai thì ù vì quán đông. Khách thì có khi giơ cái ảnh chuyển khoản… chụp từ hôm qua. Loa Loa Loa đọc to số tiền <b style="color:#fff">ngay giây ngân hàng báo về</b> — không liếc màn hình, không sợ ảnh giả, không sót đơn nào.</p>
  </div>
</div></section>

<section id="features" style="padding-top:24px"><div class="wrap">
  <div class="eyebrow">Tính năng</div>
  <h2>Mọi thứ một quán cần để “nghe được tiền”</h2>
  <p class="sec-lead">Đọc tiền, chuyển tiếp, báo cáo, widget — gọn trong một app, chạy bền cả ngày.</p>
  <div class="grid">
    <div class="card"><div class="emo">🔊</div><h3>Đọc tiền tức thì</h3><p>“Tiền vào năm trăm nghìn đồng” vang lên ngay giây thông báo tới. Có chuông báo, cho lặp lại, hiện tổng ngày.</p></div>
    <div class="card"><div class="emo">🏦</div><h3>Hợp ngân hàng bạn xài</h3><p>Đọc đúng thông báo MB Bank, Vietcombank, Techcombank… Thêm app khác cũng được.</p></div>
    <div class="card"><div class="emo">📡</div><h3>Đa thiết bị (E2E)</h3><p>Máy có app ngân hàng bắn giao dịch sang mọi máy khác qua FCM. Máy chủ chỉ thấy chuỗi mã hoá.</p></div>
    <div class="card"><div class="emo">🌐</div><h3>Nghe trên web</h3><p>Mở một đường link là nghe đọc giao dịch ngay trên trình duyệt — khỏi cần cài app.</p></div>
    <div class="card"><div class="emo">🔗</div><h3>Chuyển tiếp khắp nơi</h3><p>Đẩy mỗi giao dịch sang Telegram, Google Sheets, POS, n8n. Nhiều đích, lọc theo ca / tiền vào-ra.</p></div>
    <div class="card"><div class="emo">📊</div><h3>Báo cáo &amp; tăng trưởng</h3><p>Thu nhập ngày/tuần/tháng, xu hướng, giờ cao điểm, chia theo ngân hàng, xuất CSV.</p></div>
    <div class="card"><div class="emo">🧾</div><h3>Chốt ca bàn giao</h3><p>Theo dõi tổng tiền ca hiện tại, bàn giao gọn giữa các nhân viên.</p></div>
    <div class="card"><div class="emo">🧩</div><h3>Widget màn hình chính</h3><p>Liếc một cái thấy tổng tiền hôm nay + trạng thái dịch vụ. Dựng bằng Jetpack Glance.</p></div>
    <div class="card"><div class="emo">🔁</div><h3>Sống sót pin &amp; reboot</h3><p>Foreground service + WorkManager + boot receiver giữ bộ lắng nghe không chết.</p></div>
  </div>
</div></section>

<section id="how" style="background:#eafffb"><div class="wrap">
  <div class="eyebrow">Cách hoạt động</div>
  <h2>Bốn bước, không cấu hình rắc rối</h2>
  <p class="sec-lead">Đọc thẳng thông báo của chính ngân hàng — không hack trợ năng, không SMS, không đăng nhập.</p>
  <div class="steps">
    <div class="step"><h3>Bắt</h3><p>NotificationListenerService đọc thông báo của chính app ngân hàng.</p></div>
    <div class="step"><h3>Tách</h3><p>Bộ regex bóc số tiền, chiều vào/ra, ngân hàng và nội dung chuyển khoản.</p></div>
    <div class="step"><h3>Đọc</h3><p>Text-to-Speech đọc tiếng Việt, chuông báo, lưu vào Room database trên máy.</p></div>
    <div class="step"><h3>Chuyển tiếp</h3><p>Mã hoá rồi đẩy qua Cloudflare Worker → bắn sang mọi máy/tab bằng FCM.</p></div>
  </div>
</div></section>

<section id="screens"><div class="wrap">
  <div class="eyebrow">Giao diện</div>
  <h2>Sạch, tiếng Việt, dễ cho người bán</h2>
  <p class="sec-lead">Thiết kế Material 3 — tổng tiền nổi bật, giao dịch rõ ràng, báo cáo trực quan.</p>
  <div class="shots">
    <div class="shot"><img src="${SHOTS}/01-home.png" loading="lazy" alt="Trang chủ" /><div class="cap">Trang chủ · tổng tiền hôm nay</div></div>
    <div class="shot"><img src="${SHOTS}/03-report.png" loading="lazy" alt="Báo cáo" /><div class="cap">Báo cáo · tăng trưởng &amp; xu hướng</div></div>
    <div class="shot"><img src="${SHOTS}/04-webhook.png" loading="lazy" alt="Chuyển tiếp" /><div class="cap">Chuyển tiếp · Telegram / Sheet / POS</div></div>
    <div class="shot"><img src="${SHOTS}/02-history.png" loading="lazy" alt="Lịch sử" /><div class="cap">Lịch sử · lọc &amp; tìm giao dịch</div></div>
    <div class="shot"><img src="${SHOTS}/05-settings.png" loading="lazy" alt="Cài đặt" /><div class="cap">Cài đặt · giọng đọc &amp; thiết bị</div></div>
    <div class="shot"><img src="${SHOTS}/06-debug.png" loading="lazy" alt="Gửi thử" /><div class="cap">Gửi thử · nghe ngay khỏi cần ngân hàng</div></div>
  </div>
</div></section>

<section id="secure" style="background:#eafffb"><div class="wrap">
  <div class="eyebrow">Riêng tư &amp; bảo mật</div>
  <h2>Tiền của bạn, dữ liệu của bạn</h2>
  <p class="sec-lead">Riêng tư từ gốc — không chỗ nào chạm vào tài khoản ngân hàng của bạn.</p>
  <div class="secure">
    <div class="sec-item"><div class="emo">🚫</div><div><b>Không hỏi mật khẩu ngân hàng</b><span>Chỉ đọc thông báo mà ngân hàng vốn đã hiện cho bạn. Không xin, không lưu thông tin đăng nhập.</span></div></div>
    <div class="sec-item"><div class="emo">📱</div><div><b>Ưu tiên ngay trên máy</b><span>Tách dữ liệu, database giao dịch, báo cáo — tất cả nằm trên điện thoại của bạn.</span></div></div>
    <div class="sec-item"><div class="emo">🔐</div><div><b>Relay mã hoá đầu-cuối</b><span>AES-256-GCM ở hub, chỉ giải mã ở thiết bị đã ghép. Cloudflare chỉ giữ chuỗi mã hoá.</span></div></div>
    <div class="sec-item"><div class="emo">📖</div><div><b>Mã nguồn mở</b><span>Đọc thẳng mã để xem app làm đúng những gì. Tự host relay được.</span></div></div>
  </div>
</div></section>

<section id="get"><div class="wrap">
  <div class="final">
    <div style="font-size:46px">📣</div>
    <h2>Để cả quán cùng nghe tiền về</h2>
    <p>Tải về trong một phút, hoặc thử ngay trên web mà không cần cài gì.</p>
    <div class="cta-row" style="justify-content:center">
      <a class="btn btn-primary" href="${APK}">⬇️ Tải APK</a>
      <a class="btn btn-ghost" href="${WEB_DEMO}">🌐 Thử trên web</a>
    </div>
  </div>
</div></section>

<footer><div class="wrap">
  Làm cho người bán hàng Việt — những người chỉ mong nghe được tiếng tiền về. 📣<br/>
  <a href="${REPO}">GitHub</a> · <a href="${WEB_DEMO}">Web demo</a> · <a href="${APK}">Tải APK</a> · © <span id="yr"></span> Loa Loa Loa
</div></footer>

<script>document.getElementById('yr').textContent=new Date().getFullYear();</script>
</body>
</html>`;
}
