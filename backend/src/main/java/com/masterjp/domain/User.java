package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

@Document("users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email; // lowercase

    private String passwordHash; // BCrypt
    private String name;
    private Role role = Role.LEARNER;
    private Plan plan = Plan.FREE; // chi ap dung cho LEARNER
    private Date planExpiresAt; // null = khong thoi han
    private boolean emailVerified;
    private int streakDays;
    private Date lastStudyDate; // ngay hoc gan nhat (tinh streak)
    private int failedLoginAttempts;
    private Date lockedUntil; // khoa tam sau 5 lan sai mat khau
    private Date createdAt = new Date();
    // Phase C — F-042 gamification (them, khong doi field cu)
    // Phase 1: partial index — chi index users opt-in, tranh COLLSCAN toan collection
    @Indexed(partialFilter = "{ 'leaderboardOptIn': true }")
    private boolean leaderboardOptIn = false; // mac dinh KHONG hien tren bang xep hang
    private String nickname; // biet danh tu dat (1-20 ky tu); null -> "Tanu-" + 4 ky tu cuoi id
    // Phase D — F-046 email nhac on tap (them, khong doi field cu)
    private Boolean emailReminderOptIn = true; // mac dinh opt-in; user cu (null) duoc coi nhu true

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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public Date getPlanExpiresAt() {
        return planExpiresAt;
    }

    public void setPlanExpiresAt(Date planExpiresAt) {
        this.planExpiresAt = planExpiresAt;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = streakDays;
    }

    public Date getLastStudyDate() {
        return lastStudyDate;
    }

    public void setLastStudyDate(Date lastStudyDate) {
        this.lastStudyDate = lastStudyDate;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Date getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Date lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    // Phase C — F-042 (additive)
    public boolean isLeaderboardOptIn() {
        return leaderboardOptIn;
    }

    public void setLeaderboardOptIn(boolean leaderboardOptIn) {
        this.leaderboardOptIn = leaderboardOptIn;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    // Phase D — F-046 (additive)
    public Boolean getEmailReminderOptIn() {
        return emailReminderOptIn;
    }

    public void setEmailReminderOptIn(Boolean emailReminderOptIn) {
        this.emailReminderOptIn = emailReminderOptIn;
    }

    /** User hien co (null) duoc coi nhu opt-in — khong can migration script. */
    public boolean isEmailReminderOptIn() {
        return !Boolean.FALSE.equals(emailReminderOptIn);
    }
}
