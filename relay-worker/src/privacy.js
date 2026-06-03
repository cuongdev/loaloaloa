/**
 * Vietnamese privacy policy page served at GET /privacy.
 * Required for the Google Play Store listing.
 *
 * Visual style mirrors landing.js (same CSS variables, nav header with logo, footer).
 */

const LOGO_SVG = `<svg width="44" height="44" viewBox="0 0 64 64" role="img" aria-label="Loa Loa Loa"><g fill="#0EA5A4"><path d="M6 26h8v12H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z"/><path d="M14 24 42 14v36L14 40z"/><path d="M20 40h6v9a3 3 0 0 1-6 0z"/></g><path d="M48 24q6 8 0 16" fill="none" stroke="#0EA5A4" stroke-width="3.5" stroke-linecap="round"/><path d="M54 19q10 13 0 26" fill="none" stroke="#5EEAD4" stroke-width="3.5" stroke-linecap="round"/></svg>`;

export function privacyPage() {
  return `<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
<title>Chính sách quyền riêng tư — Loa Loa Loa</title>
<meta name="description" content="Chính sách quyền riêng tư của ứng dụng Loa Loa Loa — đọc to biến động số dư từ thông báo ngân hàng." />
<meta name="theme-color" content="#0EA5A4" />
<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Cg fill='%230EA5A4'%3E%3Cpath d='M6 26h8v12H6a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z'/%3E%3Cpath d='M14 24 42 14v36L14 40z'/%3E%3Cpath d='M20 40h6v9a3 3 0 0 1-6 0z'/%3E%3C/g%3E%3C/svg%3E" />
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  :root{
    --teal:#0EA5A4; --teal-d:#0b7d7c; --mint:#5EEAD4; --ink:#0f2e2d; --muted:#5b6b6a;
    --bg:#f3faf9; --card:#ffffff; --line:#e2efed;
  }
  html{scroll-behavior:smooth}
  body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",sans-serif;
       color:var(--ink); background:var(--bg); line-height:1.7; -webkit-font-smoothing:antialiased}
  a{color:var(--teal-d); text-decoration:none}
  a:hover{text-decoration:underline}
  .wrap{max-width:800px; margin:0 auto; padding:0 22px}

  /* nav */
  nav{position:sticky; top:0; z-index:20; backdrop-filter:saturate(180%) blur(12px);
      background:rgba(243,250,249,.82); border-bottom:1px solid var(--line)}
  nav .wrap{display:flex; align-items:center; justify-content:space-between; height:64px}
  .brand{display:flex; align-items:center; gap:10px; font-weight:800; font-size:18px; color:var(--ink); text-decoration:none}
  .brand:hover{text-decoration:none}
  .nav-back{font-size:14px; font-weight:600; color:var(--muted)}
  .nav-back:hover{color:var(--ink); text-decoration:none}

  /* content */
  .page-header{padding:52px 0 36px; border-bottom:1px solid var(--line); margin-bottom:40px}
  .page-header .eyebrow{color:var(--teal-d); font-weight:800; letter-spacing:.04em;
                         text-transform:uppercase; font-size:13px; margin-bottom:10px}
  .page-header h1{font-size:32px; line-height:1.15; letter-spacing:-.02em; font-weight:850; margin-bottom:10px}
  .page-header .updated{color:var(--muted); font-size:14px}

  .prose{padding-bottom:72px}
  .prose h2{font-size:20px; font-weight:800; margin:36px 0 10px; color:var(--ink)}
  .prose h2:first-child{margin-top:0}
  .prose p{color:#334444; font-size:16px; margin-bottom:14px}
  .prose ul{color:#334444; font-size:16px; margin:0 0 14px 0; padding-left:22px}
  .prose ul li{margin-bottom:7px}
  .prose strong{color:var(--ink)}
  .prose .note{background:#e9fbf6; border-left:3px solid var(--teal); padding:14px 16px;
               border-radius:0 10px 10px 0; font-size:15px; color:var(--teal-d); margin:18px 0}

  footer{padding:40px 0 56px; color:var(--muted); font-size:14px; text-align:center;
         border-top:1px solid var(--line); margin-top:24px}
  footer a{color:var(--teal-d); text-decoration:none; font-weight:600}
  footer a:hover{text-decoration:underline}
</style>
</head>
<body>

<nav><div class="wrap">
  <a class="brand" href="/">${LOGO_SVG}<span>Loa Loa Loa</span></a>
  <a class="nav-back" href="/">&#8592; Trang chủ</a>
</div></nav>

<main>
<div class="wrap">

<div class="page-header">
  <div class="eyebrow">Pháp lý</div>
  <h1>Chính sách quyền riêng tư</h1>
  <div class="updated">Cập nhật: 06/2026</div>
</div>

<div class="prose">

<h2>1. Mở đầu</h2>
<p><strong>Loa Loa Loa</strong> là ứng dụng Android đọc to biến động số dư từ thông báo ngân hàng — giúp người bán hàng nghe ngay khi có tiền chuyển khoản về mà không cần liếc màn hình. Quá trình xử lý chính diễn ra <strong>ngay trên thiết bị của bạn</strong>; chỉ một lượng dữ liệu tối thiểu được chuyển ra ngoài cho mục đích kết nối đa thiết bị.</p>
<p>Chính sách này giải thích rõ dữ liệu nào được truy cập, mục đích sử dụng, cách lưu trữ và quyền kiểm soát của bạn.</p>

<h2>2. Dữ liệu được truy cập</h2>
<ul>
  <li><strong>Nội dung thông báo từ ứng dụng ngân hàng</strong> — thông qua quyền đọc thông báo (<em>Notification Listener</em>) mà bạn cấp trong cài đặt hệ thống. Ứng dụng chỉ đọc thông báo để trích số tiền, chiều giao dịch (vào/ra) và nội dung chuyển khoản, rồi đọc to bằng Text-to-Speech. Thông tin đăng nhập ngân hàng không bao giờ được yêu cầu hoặc lưu trữ.</li>
  <li><strong>Camera</strong> — chỉ dùng để quét mã QR khi ghép máy nhân viên. Camera không được sử dụng cho bất kỳ mục đích nào khác.</li>
  <li><strong>FCM token (Firebase Cloud Messaging)</strong> — mã định danh thiết bị do Google cấp, dùng để định tuyến thông báo đẩy giữa máy shop và máy nhân viên đã ghép.</li>
  <li><strong>Payload giao dịch khi chia sẻ đa thiết bị</strong> — nội dung giao dịch được <strong>mã hoá đầu-cuối (E2E, AES-256-GCM)</strong> trước khi rời thiết bị; máy chủ trung gian chỉ nhận và chuyển tiếp chuỗi đã mã hoá, không thể đọc nội dung.</li>
</ul>

<h2>3. Mục đích sử dụng</h2>
<ul>
  <li>Đọc to số tiền và nội dung giao dịch trên loa — hoàn toàn trên thiết bị của bạn.</li>
  <li>Máy shop chuyển tiếp giao dịch đã mã hoá E2E tới các máy nhân viên đã ghép qua máy chủ trung gian. Máy chủ <strong>không đọc được</strong> nội dung giao dịch.</li>
  <li>Webhook (tuỳ chọn, nếu người dùng tự bật): gửi thông tin giao dịch tới endpoint do chính bạn cấu hình (ví dụ: Telegram, Google Sheets, hệ thống POS nội bộ). Ứng dụng không kiểm soát nơi bạn chọn gửi dữ liệu.</li>
  <li>Tích hợp SePay API (tuỳ chọn): nếu bạn cấu hình, ứng dụng lấy dữ liệu giao dịch từ SePay để bổ sung hoặc thay thế nguồn thông báo.</li>
</ul>

<h2>4. Lưu trữ dữ liệu</h2>
<p>Lịch sử giao dịch được lưu trong <strong>cơ sở dữ liệu cục bộ trên thiết bị của bạn</strong> (Room database). Máy chủ relay chỉ lưu FCM token để định tuyến thông báo đẩy; máy chủ <strong>không lưu nội dung giao dịch</strong> ở dạng có thể đọc được.</p>
<div class="note">Máy chủ trung gian chỉ lưu trữ chuỗi đã mã hoá E2E — ngay cả đội ngũ phát triển ứng dụng cũng không thể giải mã nội dung này.</div>

<h2>5. Chia sẻ với bên thứ ba</h2>
<p>Chúng tôi <strong>không bán</strong> và <strong>không chia sẻ</strong> dữ liệu cá nhân của bạn cho bất kỳ bên thứ ba nào vì mục đích thương mại.</p>
<ul>
  <li><strong>Firebase Cloud Messaging (Google)</strong>: đóng vai trò kênh vận chuyển thông báo đẩy. Google chỉ nhận được chuỗi đã mã hoá E2E và FCM token; không nhận được nội dung giao dịch rõ ràng.</li>
  <li><strong>Webhook do người dùng cấu hình</strong>: dữ liệu giao dịch được gửi tới endpoint bạn tự thiết lập; ứng dụng không kiểm soát bên nhận đó.</li>
  <li><strong>SePay API</strong>: nếu bạn kích hoạt, ứng dụng kết nối tới dịch vụ SePay theo thông tin bạn cung cấp.</li>
</ul>

<h2>6. Quyền &amp; kiểm soát của bạn</h2>
<ul>
  <li>Bạn có thể <strong>thu hồi quyền đọc thông báo</strong> bất cứ lúc nào trong phần Cài đặt hệ thống &gt; Ứng dụng &gt; Loa Loa Loa &gt; Quyền (hoặc Cài đặt &gt; Thông báo &gt; Quyền truy cập thông báo).</li>
  <li>Bạn có thể <strong>thu hồi quyền camera</strong> trong Cài đặt hệ thống bất cứ lúc nào.</li>
  <li>Webhook và tích hợp SePay có thể tắt hoàn toàn trong màn hình Cài đặt của ứng dụng.</li>
</ul>

<h2>7. Xoá dữ liệu</h2>
<ul>
  <li><strong>Gỡ cài đặt ứng dụng</strong> sẽ xoá toàn bộ dữ liệu cục bộ (lịch sử giao dịch, cấu hình, khoá mã hoá).</li>
  <li><strong>Huỷ ghép thiết bị</strong> (từ máy shop hoặc từ màn hình Thiết bị trong ứng dụng) sẽ xoá FCM token của máy đó khỏi phòng trên máy chủ relay.</li>
  <li>Không có tài khoản người dùng trên máy chủ — không cần liên hệ để yêu cầu xoá tài khoản.</li>
</ul>

<h2>8. Bảo mật</h2>
<p>Mọi dữ liệu giao dịch được mã hoá đầu-cuối bằng AES-256-GCM trước khi rời thiết bị. Khoá mã hoá chỉ tồn tại trên các thiết bị đã ghép của bạn. Ứng dụng là mã nguồn mở — bạn có thể xem mã nguồn trực tiếp để kiểm chứng.</p>

<h2>9. Liên hệ</h2>
<p>Nếu bạn có câu hỏi về chính sách này hoặc muốn yêu cầu xoá dữ liệu, vui lòng liên hệ qua email: <a href="mailto:lienhe@loaloaloa.app">lienhe@loaloaloa.app</a></p>

<h2>10. Cập nhật chính sách</h2>
<p>Chúng tôi có thể cập nhật chính sách này khi tính năng của ứng dụng thay đổi. Phiên bản mới nhất luôn được đăng tại địa chỉ <a href="/privacy">loaloaloa.haveuever.workers.dev/privacy</a>. Ngày cập nhật gần nhất được ghi ở đầu trang.</p>

</div><!-- .prose -->
</div><!-- .wrap -->
</main>

<footer><div class="wrap">
  Làm cho người bán hàng Việt — những người chỉ mong nghe được tiếng tiền về.<br/>
  <a href="/">Trang chủ</a> · <a href="/privacy">Chính sách quyền riêng tư</a> · © <span id="yr"></span> Loa Loa Loa
</div></footer>

<script>document.getElementById('yr').textContent=new Date().getFullYear();</script>
</body>
</html>`;
}
