package com.masterjp.service;

import com.masterjp.domain.OtpCode;
import com.masterjp.domain.OtpPurpose;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.OtpCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;

/**
 * OTP 6 so (F-001): hash SHA-256 trong DB, TTL 5 phut, toi da 5 lan sai,
 * rate-limit 1 ma/phut/email.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final long RESEND_INTERVAL_MS = 60_000; // 1 phut
    private static final long TTL_MS = 5 * 60_000; // 5 phut
    private static final int MAX_ATTEMPTS = 5;

    private final OtpCodeRepository otpCodeRepository;
    private final MailService mailService;
    private final boolean devLogCode;

    public OtpService(OtpCodeRepository otpCodeRepository,
                      MailService mailService,
                      @Value("${app.mail.dev-log-code:false}") boolean devLogCode) {
        this.otpCodeRepository = otpCodeRepository;
        this.mailService = mailService;
        this.devLogCode = devLogCode;
    }

    /**
     * Xin ma OTP. Luon tra message giong nhau de khong lo email da ton tai.
     * Neu purpose=REGISTER ma email da co tai khoan -> gui mail thong bao
     * "tai khoan da ton tai" thay vi ma OTP.
     */
    public void requestCode(String email, OtpPurpose purpose, boolean userExists) {
        String normalized = email.trim().toLowerCase();

        otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(normalized).ifPresent(latest -> {
            if (System.currentTimeMillis() - latest.getCreatedAt().getTime() < RESEND_INTERVAL_MS) {
                throw new ApiException("OTP_RATE_LIMITED",
                        "Bạn vừa yêu cầu mã. Vui lòng đợi ít nhất 1 phút trước khi gửi lại.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
        });

        if (purpose == OtpPurpose.REGISTER && userExists) {
            mailService.sendAccountExistsNotice(normalized);
            return;
        }

        if (purpose == OtpPurpose.RESET_PASSWORD && !userExists) {
            return; // no-op im lặng: controller vẫn trả message chung chung,
                    // không gửi mail, không lưu OTP (chống enumeration / spam vector)
        }

        String code = String.format("%06d", new SecureRandom().nextInt(900_000) + 100_000);
        OtpCode otp = new OtpCode();
        otp.setEmail(normalized);
        otp.setCodeHash(sha256(code));
        otp.setPurpose(purpose);
        otp.setAttempts(0);
        otp.setExpiresAt(new Date(System.currentTimeMillis() + TTL_MS));
        otpCodeRepository.save(otp);

        if (devLogCode) {
            log.warn("[DEV-ONLY] Ma OTP cho {}: {}", normalized, code);
        }
        mailService.sendOtpEmail(normalized, code, purpose);
    }

    /** Xac thuc ma: dung -> xoa OTP; sai -> tang attempts (toi da 5). */
    public void verifyCode(String email, String code, OtpPurpose purpose) {
        String normalized = email.trim().toLowerCase();
        OtpCode otp = otpCodeRepository
                .findFirstByEmailAndPurposeOrderByCreatedAtDesc(normalized, purpose)
                .orElseThrow(() -> new ApiException("OTP_INVALID",
                        "Mã xác thực không đúng hoặc đã hết hạn.", HttpStatus.BAD_REQUEST));

        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            otpCodeRepository.delete(otp);
            throw new ApiException("OTP_TOO_MANY_ATTEMPTS",
                    "Bạn đã nhập sai quá 5 lần. Vui lòng yêu cầu mã mới.", HttpStatus.BAD_REQUEST);
        }
        if (otp.getExpiresAt().before(new Date())) {
            otpCodeRepository.delete(otp);
            throw new ApiException("OTP_EXPIRED",
                    "Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới.", HttpStatus.BAD_REQUEST);
        }
        String inputHash = sha256(code == null ? "" : code.trim());
        boolean match = MessageDigest.isEqual(
                otp.getCodeHash().getBytes(StandardCharsets.UTF_8),
                inputHash.getBytes(StandardCharsets.UTF_8));
        if (!match) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpCodeRepository.save(otp);
            int left = MAX_ATTEMPTS - otp.getAttempts();
            throw new ApiException("OTP_INVALID",
                    "Mã xác thực không đúng. Bạn còn " + left + " lượt thử.", HttpStatus.BAD_REQUEST);
        }
        otpCodeRepository.deleteByEmailAndPurpose(normalized, purpose);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thieu thuat toan SHA-256", e);
        }
    }
}
