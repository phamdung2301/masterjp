package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Phase C — F-042 Gamification: huy hieu da dat cua user.
 * Catalog 12 huy hieu hard-code trong {@code BadgeCatalog} (backend), KHONG bang DB rieng.
 * Unique index (userId, badgeCode) chong trao trung khi 2 session hoan thanh song song.
 */
@Document("user_badges")
@CompoundIndexes({
        @CompoundIndex(name = "uq_user_badge", def = "{'userId': 1, 'badgeCode': 1}", unique = true)
})
public class UserBadge {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String badgeCode; // 1 trong 12 code cua BadgeCatalog
    private Date earnedAt = new Date();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getBadgeCode() {
        return badgeCode;
    }

    public void setBadgeCode(String badgeCode) {
        this.badgeCode = badgeCode;
    }

    public Date getEarnedAt() {
        return earnedAt;
    }

    public void setEarnedAt(Date earnedAt) {
        this.earnedAt = earnedAt;
    }
}
