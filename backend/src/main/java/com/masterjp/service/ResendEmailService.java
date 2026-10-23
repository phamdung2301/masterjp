package com.masterjp.service;

import com.masterjp.domain.Invoice;
import com.masterjp.domain.PlanType;
import com.masterjp.domain.User;
import com.masterjp.util.AppTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Phase D — F-046: gui email qua Resend API (POST https://api.resend.com/emails).
 * <p>
 * Song song voi {@link MailService} (Gmail SMTP, plain-text, dung cho OTP) —
 * KHONG refactor, khong dung chung interface de tranh regression luong dang nhap.
 * <p>
 * - Thieu ENV (RESEND_API_KEY / RESEND_FROM_EMAIL) -> fail-fast luc startup.
 * - Gui email la best-effort: fail -> log error (khong log API key), khong throw
 *   len caller, khong rollback nghiep vu da commit.
 * - Template HTML washi (inline CSS), giong Tanu-chan, khong emoji.
 */
@Service
public class ResendEmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";

    // Palette washi (binding, DOC-13 muc 5.5)
    private static final String BG = "#FAF7F0"; // kem
    private static final String INDIGO = "#2B3A67"; // cham
    private static final String VERMILION = "#EB471F"; // do shu-iro
    private static final String TEXT = "#333333";

    private final RestTemplate restTemplate = createRestTemplate();

    /** Timeout 10s: tranh treo thread webhook khi api.resend.com khong phan hoi. */
    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
    private final String apiKey;
    private final String fromEmail;
    private final String frontendUrl;
    private final ZoneId zoneId;

    public ResendEmailService(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from-email:}") String fromEmail,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            AppTime appTime) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Thieu ENV bat buoc RESEND_API_KEY (F-046 email qua Resend). "
                            + "Them vao backend/.env hoac Render Dashboard -> Environment, roi khoi dong lai. "
                            + "Tu choi khoi dong thay vi lang le tat email.");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException(
                    "Thieu ENV bat buoc RESEND_FROM_EMAIL (F-046 email qua Resend) "
                            + "— dia chi gui phai la domain da verify trong Resend. "
                            + "Them vao backend/.env hoac Render Dashboard -> Environment, roi khoi dong lai.");
        }
        this.apiKey = apiKey.trim();
        this.fromEmail = fromEmail.trim();
        this.frontendUrl = frontendUrl != null ? frontendUrl.trim() : "";
        this.zoneId = appTime.zoneId();
    }

    // ---------- Public API (best-effort, khong throw) ----------

    /** Template 1 — welcome, trigger sau khi user verify OTP dang ky xong. */
    public void sendWelcomeEmail(User user) {
        String name = displayName(user);
        String html = washiShell("Chào mừng bạn đến với Master JP!",
                "<p class=\"hi\">Chào " + esc("「" + name + "」") + ",</p>"
                        + "<p>Tanu-chan đây! Cảm ơn bạn đã tham gia Master JP. "
                        + "Mình sẽ đồng hành cùng bạn trên hành trình chinh phục tiếng Nhật, "
                        + "mỗi ngày một chút thôi là đủ.</p>"
                        + "<h3>Bắt đầu trong 3 bước:</h3>"
                        + "<ol>"
                        + "<li><b>Học Bài 1</b> ở tab Học — từ vựng theo giáo trình Minna no Nihongo.</li>"
                        + "<li><b>Ôn bằng flashcard 3D</b> — lật thẻ mỗi ngày để nhớ lâu.</li>"
                        + "<li><b>Giữ streak mỗi ngày</b> — đều đặn quan trọng hơn học nhiều một lúc.</li>"
                        + "</ol>"
                        + "<p class=\"muted\">Gói Free cho phép bạn học 20 từ mới mỗi ngày. "
                        + "Khi cần học không giới hạn, bạn có thể nâng cấp Premium trong mục Cá nhân.</p>"
                        + cta("Bắt đầu học ngay", frontendUrl));
        send(user.getEmail(),
                "Chào mừng bạn đến với Master JP — Tanu-chan đồng hành cùng bạn!",
                html);
    }

    /** Template 2 — payment success, trigger SAU KHI transaction fulfill commit xong. */
    public void sendPaymentSuccessEmail(User user, Invoice invoice, Date newPlanExpiresAt) {
        String expiry = newPlanExpiresAt == null ? "—"
                : DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        .format(newPlanExpiresAt.toInstant().atZone(zoneId));
        String html = washiShell("Thanh toán thành công!",
                "<p class=\"hi\">Cảm ơn bạn, " + esc("「" + displayName(user) + "」") + "!</p>"
                        + "<p>Master JP đã nhận được thanh toán của bạn. "
                        + "Tài khoản của bạn đã chính thức lên <b>Premium</b>.</p>"
                        + "<table class=\"info\">"
                        + row("Mã hóa đơn", invoice.getCode())
                        + row("Gói", planLabel(invoice.getPlanType()))
                        + row("Số tiền", formatAmountVnd(invoice.getAmount()))
                        + row("Hết hạn", expiry)
                        + "</table>"
                        + "<h3>Quyền lợi đã mở:</h3>"
                        + "<ul>"
                        + "<li>Học không giới hạn từ mới mỗi ngày.</li>"
                        + "<li>Ưu tiên Sensei AI.</li>"
                        // DOC-13 muc 5.3 [gia dinh] "huy hieu Premium" — dung ten that tren FE
                        // (paywall/trang Nang cap/Home deu goi "huy hieu Nguoi ung ho").
                        + "<li>Huy hiệu Người ủng hộ.</li>"
                        + "</ul>"
                        + cta("Khám phá Premium ngay", frontendUrl)
                        + "<p class=\"muted\">Hóa đơn điện tử (nếu có) sẽ được gửi riêng. "
                        + "Cần hỗ trợ, bạn cứ liên hệ đội ngũ Master JP nhé.</p>");
        send(user.getEmail(),
                "Thanh toán thành công — chào mừng bạn đến với Premium!",
                html);
    }

    /** Template 3 — nhac on tap 8:00 sang (chi goi khi N > 0). */
    public void sendReviewReminderEmail(User user, long dueCount) {
        String subject = "Tanu-chan nhắc bạn: hôm nay có " + dueCount + " mục chờ ôn tập!";
        String html = washiShell("Nhắc ôn tập hôm nay",
                "<p class=\"hi\">Chào buổi sáng, " + esc("「" + displayName(user) + "」") + "!</p>"
                        + "<p>Hôm nay bạn có <b>" + dueCount + " mục chờ ôn tập</b>. "
                        + "Ôn đúng lúc là bí quyết nhớ lâu đấy.</p>"
                        + streakLine(user.getStreakDays())
                        + cta("Ôn tập ngay", frontendUrl + "/on-tap")
                        + "<p class=\"muted\">Không muốn nhận email này? "
                        + "Tắt trong mục Cá nhân &rarr; Cài đặt tài khoản &rarr; Email nhắc ôn tập.</p>");
        send(user.getEmail(), subject, html);
    }

    // ---------- Gui qua Resend API ----------

    private void send(String to, String subject, String html) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> body = Map.of(
                    "from", fromEmail,
                    "to", List.of(to),
                    "subject", subject,
                    "html", html);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(RESEND_URL, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("Resend: da gui email '{}' toi {}", subject, to);
            } else {
                log.error("Resend: gui email that bai ({}): subject='{}' to={}",
                        resp.getStatusCode(), subject, to);
            }
        } catch (Exception e) {
            // Best-effort: log, khong throw, khong bao gio log API key.
            log.error("Resend: khong gui duoc email '{}' toi {}: {}",
                    subject, to, e.getMessage());
        }
    }

    // ---------- Template helpers ----------

    /** Ten goi hien thi trong email — khong lo ma enum noi bo (MONTHLY/YEARLY). */
    private String planLabel(PlanType planType) {
        if (planType == PlanType.MONTHLY) {
            return "Gói tháng";
        }
        if (planType == PlanType.YEARLY) {
            return "Gói năm";
        }
        return "Premium";
    }

    /** Format tien kieu FE: 49000 -> "49.000đ" (dinh dang khong phu thuoc JVM locale). */
    private String formatAmountVnd(long amount) {
        return String.format(java.util.Locale.ROOT, "%,d", amount).replace(',', '.') + "đ";
    }

    /** Streak = 0 thi khong hien "0 ngay lien tiep" (nghe cut hung) — khich le bat dau. */
    private String streakLine(int streakDays) {
        if (streakDays <= 0) {
            return "<p>Mỗi ngày một chút thôi là đủ — bắt đầu chuỗi ngày học của bạn ngay hôm nay!</p>";
        }
        return "<p>" + streakDays + " ngày liên tiếp — giữ lửa nhé!</p>";
    }

    private String displayName(User user) {
        return (user.getName() != null && !user.getName().isBlank())
                ? user.getName().trim() : user.getEmail();
    }

    /** Layout 1 cot, max-width 600px, inline CSS — email client khong doc external CSS. */
    private String washiShell(String title, String bodyHtml) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "</head><body style=\"margin:0;padding:0;background-color:" + BG
                + ";font-family:-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;\">"
                + "<div style=\"max-width:600px;margin:0 auto;background-color:#ffffff;\">"
                + "<div style=\"background-color:" + INDIGO + ";padding:28px 32px;\">"
                + "<div style=\"color:#ffffff;font-size:22px;font-weight:bold;\">Master JP</div>"
                + "<div style=\"color:#D9DCE8;font-size:13px;margin-top:4px;\">" + esc(title) + "</div>"
                + "</div>"
                + "<div style=\"padding:28px 32px;color:" + TEXT
                + ";font-size:15px;line-height:1.7;\">"
                + "<style>.hi{font-size:17px;font-weight:bold}.muted{color:#777;font-size:13px}"
                + ".info{border-collapse:collapse;width:100%;margin:12px 0}"
                + ".info td{border:1px solid #E5DFD3;padding:8px 12px;font-size:14px}"
                + ".info td:first-child{color:#777;width:40%}"
                + "h3{color:" + INDIGO + ";font-size:16px;margin:20px 0 8px}"
                + "ol,ul{padding-left:20px;margin:8px 0}</style>"
                + bodyHtml
                + "</div>"
                + "<div style=\"background-color:" + BG + ";padding:16px 32px;color:#999;"
                + "font-size:12px;line-height:1.6;\">"
                + "Bạn nhận email này vì đã đăng ký tài khoản Master JP.<br>"
                + "Tanu-chan — người bạn đồng hành học tiếng Nhật của bạn."
                + "</div>"
                + "</div></body></html>";
    }

    private String cta(String label, String url) {
        return "<p style=\"text-align:center;margin:24px 0;\">"
                + "<a href=\"" + esc(url) + "\" "
                + "style=\"display:inline-block;background-color:" + VERMILION
                + ";color:#ffffff;text-decoration:none;font-weight:bold;"
                + "padding:12px 32px;border-radius:8px;\">" + esc(label) + "</a></p>";
    }

    private String row(String label, String value) {
        return "<tr><td>" + esc(label) + "</td><td><b>" + esc(value) + "</b></td></tr>";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
