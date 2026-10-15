package com.masterjp.service;

import com.masterjp.domain.OtpPurpose;
import com.masterjp.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Gui mail qua Gmail SMTP (cau hinh spring.mail.* tu ENV). */
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendOtpEmail(String to, String code, OtpPurpose purpose) {
        String subject = purpose == OtpPurpose.REGISTER
                ? "Mã xác thực đăng ký Master JP"
                : "Mã đặt lại mật khẩu Master JP";
        String text = "Xin chào,\n\n"
                + "Mã xác thực của bạn là: " + code + "\n"
                + "Mã có hiệu lực trong 5 phút và chỉ dùng được 1 lần.\n\n"
                + "Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.\n\n"
                + "— Đội ngũ Master JP";
        send(to, subject, text);
    }

    /** F-001: email da dang ky -> khong gui OTP, chi bao "tai khoan da ton tai". */
    public void sendAccountExistsNotice(String to) {
        String subject = "Tài khoản Master JP đã tồn tại";
        String text = "Xin chào,\n\n"
                + "Email này đã được đăng ký tài khoản Master JP.\n"
                + "Nếu đó là bạn, hãy đăng nhập hoặc dùng chức năng \"Quên mật khẩu\".\n\n"
                + "— Đội ngũ Master JP";
        send(to, subject, text);
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (MailException e) {
            throw new ApiException("MAIL_SEND_FAILED",
                    "Không gửi được email. Vui lòng kiểm tra lại địa chỉ email và thử lại sau.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
