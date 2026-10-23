package com.masterjp.service;

import com.masterjp.domain.Invoice;
import com.masterjp.domain.PlanType;
import com.masterjp.domain.User;
import com.masterjp.util.AppTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho ResendEmailService (Phase D — F-046).
 * Service tự new RestTemplate() bên trong -> dùng Mockito mockConstruction
 * để chặn HTTP layer — KHÔNG gọi https://api.resend.com thật.
 * Yêu cầu mockito-core 5+ (inline mockmaker mặc định từ Spring Boot 3.2+).
 * Chay bang: mvn test -Dtest=ResendEmailServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResendEmailService — gửi email qua Resend API")
class ResendEmailServiceTest {

    @Mock
    private AppTime appTime;

    private ResendEmailService newService() {
        when(appTime.zoneId()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        return new ResendEmailService("re_test_key_123", "chao@masterjp.vn",
                "https://app.masterjp.vn", appTime);
    }

    private User user() {
        User u = new User();
        u.setId("u1");
        u.setEmail("hocvien@example.com");
        u.setName("Học Viên");
        u.setStreakDays(7);
        return u;
    }

    /** Chặn construction RestTemplate, stub POST trả 200, trả về mock để verify. */
    private RestTemplate stubOk(MockedConstruction<RestTemplate> mocked) {
        RestTemplate mockRt = mocked.constructed().get(0);
        when(mockRt.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"id\":\"mail-1\"}", HttpStatus.OK));
        return mockRt;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureBody(RestTemplate mockRt) {
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(mockRt).postForEntity(
                eq("https://api.resend.com/emails"), entityCaptor.capture(), eq(String.class));
        HttpEntity<Map<String, Object>> entity = entityCaptor.getValue();
        HttpHeaders headers = entity.getHeaders();
        // Shape bắt buộc của Resend API
        assertEquals("Bearer re_test_key_123", headers.getFirst(HttpHeaders.AUTHORIZATION),
                "Authorization phải là 'Bearer <RESEND_API_KEY>'");
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        return entity.getBody();
    }

    @Test
    @DisplayName("Welcome email: đúng URL, Bearer auth, from/to/subject/html")
    void sendWelcomeEmail_guiDungShapeResend() {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class)) {
            ResendEmailService svc = newService();
            RestTemplate mockRt = stubOk(mocked);

            svc.sendWelcomeEmail(user());

            Map<String, Object> body = captureBody(mockRt);
            assertEquals("chao@masterjp.vn", body.get("from"));
            assertEquals(List.of("hocvien@example.com"), body.get("to"));
            assertTrue(((String) body.get("subject")).contains("Master JP"));
            String html = (String) body.get("html");
            assertTrue(html.contains("Tanu-chan"), "template phải có Tanu-chan");
            assertTrue(html.contains("20 từ mới"), "welcome phải nhắc trần Free 20 từ/ngày");
            assertTrue(html.contains("https://app.masterjp.vn"), "CTA phải trỏ về frontendUrl");
        }
    }

    @Test
    @DisplayName("Payment-success email: subject Premium, html có mã HĐ + số tiền + hạn")
    void sendPaymentSuccessEmail_guiDungShape() {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class)) {
            ResendEmailService svc = newService();
            RestTemplate mockRt = stubOk(mocked);
            Invoice inv = new Invoice();
            inv.setCode("INV-ABC123");
            inv.setPlanType(PlanType.MONTHLY);
            inv.setAmount(49_000L);

            svc.sendPaymentSuccessEmail(user(), inv, new Date(1_780_000_000_000L));

            Map<String, Object> body = captureBody(mockRt);
            assertTrue(((String) body.get("subject")).contains("Premium"));
            String html = (String) body.get("html");
            assertTrue(html.contains("INV-ABC123"), "phải có mã hóa đơn");
            assertTrue(html.contains("49.000đ"), "số tiền format 49.000đ, nhận: " + html);
            assertTrue(html.contains("27/05/2026"), "hạn dd/MM/yyyy theo Asia/Ho_Chi_Minh");
            assertEquals(List.of("hocvien@example.com"), body.get("to"));
        }
    }

    @Test
    @DisplayName("Review-reminder email: subject có dueCount, CTA tới /on-tap")
    void sendReviewReminderEmail_guiDungShape() {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class)) {
            ResendEmailService svc = newService();
            RestTemplate mockRt = stubOk(mocked);

            svc.sendReviewReminderEmail(user(), 12);

            Map<String, Object> body = captureBody(mockRt);
            assertTrue(((String) body.get("subject")).contains("12"),
                    "subject phải chứa số mục chờ ôn");
            String html = (String) body.get("html");
            assertTrue(html.contains("12"), "body phải chứa dueCount");
            assertTrue(html.contains("https://app.masterjp.vn/on-tap"),
                    "CTA phải tới trang ôn tập");
        }
    }

    @Test
    @DisplayName("Resend trả non-2xx (400): best-effort — không ném, không crash caller")
    void send_resendTra400_khongThrow() {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class)) {
            ResendEmailService svc = newService();
            RestTemplate mockRt = mocked.constructed().get(0);
            when(mockRt.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("{\"error\":\"bad\"}",
                            HttpStatus.BAD_REQUEST));

            assertDoesNotThrow(() -> svc.sendWelcomeEmail(user()));
        }
    }

    @Test
    @DisplayName("Lỗi mạng (ResourceAccessException): best-effort — log và nuốt, không ném")
    void send_loiMang_khongThrow() {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class)) {
            ResendEmailService svc = newService();
            RestTemplate mockRt = mocked.constructed().get(0);
            when(mockRt.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new ResourceAccessException("I/O error"));

            // Email fail KHÔNG được rollback nghiệp vụ đã commit (fulfill thanh toán)
            assertDoesNotThrow(() -> svc.sendWelcomeEmail(user()));
        }
    }

    // ================= Fail-fast ENV =================

    @Test
    @DisplayName("Thiếu RESEND_API_KEY -> fail-fast lúc khởi tạo, không boot app")
    void constructor_thieuApiKey_failFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ResendEmailService("", "chao@masterjp.vn",
                        "https://app.masterjp.vn", appTime));

        assertTrue(e.getMessage().contains("RESEND_API_KEY"));
    }

    @Test
    @DisplayName("Thiếu RESEND_FROM_EMAIL -> fail-fast lúc khởi tạo")
    void constructor_thieuFromEmail_failFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ResendEmailService("re_key", "  ",
                        "https://app.masterjp.vn", appTime));

        assertTrue(e.getMessage().contains("RESEND_FROM_EMAIL"));
    }

    @Test
    @DisplayName("Đủ ENV: khởi tạo OK, không ném")
    void constructor_duEnv_ok() {
        assertDoesNotThrow(this::newService);
    }
}
