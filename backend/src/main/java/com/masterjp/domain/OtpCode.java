package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.document.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * Ma OTP 6 so: chi luu SHA-256 hash, khong luu ma tran.
 * Document tu xoa nho TTL index tren expiresAt (expireAfter = "0" -> xoa dung
 * tai thoi diem expiresAt).
 */
@Document("otp_codes")
public class OtpCode {

    @Id
    private String id;

    @Indexed
    private String email;

    private String codeHash; // SHA-256 hex cua ma 6 so
    private OtpPurpose purpose;
    private int attempts; // so lan nhap sai (toi da 5)
    private Date createdAt = new Date();

    @Indexed(expireAfter = "0")
    private Date expiresAt; // hieu luc 5 phut

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }
}
