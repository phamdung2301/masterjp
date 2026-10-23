package com.masterjp.dto;

import java.util.Date;

/**
 * Phase D — F-046: DTO cho cai dat ca nhan (toggle email nhac on tap).
 * Ten field la contract tuyet doi voi FE (api-contract-guard se verify).
 */
public final class UserDtos {

    private UserDtos() {
    }

    /**
     * PATCH /api/users/me — request: {"emailReminderOptIn": true}.
     * (Lua chon chinh theo DOC-13 muc 9.)
     */
    public record UpdateMySettingsRequest(Boolean emailReminderOptIn) {
    }

    /** PATCH /api/users/me — response: user sau khi cap nhat. */
    public record MySettingsResponse(String id, String email, String name, String role,
                                     String plan, Date planExpiresAt, int streakDays,
                                     boolean emailReminderOptIn) {
    }
}
