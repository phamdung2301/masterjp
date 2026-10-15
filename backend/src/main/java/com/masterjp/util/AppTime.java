package com.masterjp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Mui gio cua app (mac dinh Asia/Ho_Chi_Minh) — dung cho streak, thong ke ngay.
 * Server Render chay UTC nen KHONG dung systemDefault.
 */
@Component
public class AppTime {

    private final ZoneId zoneId;

    public AppTime(@Value("${app.timezone:Asia/Ho_Chi_Minh}") String zoneId) {
        this.zoneId = ZoneId.of(zoneId);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    public Date startOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(zoneId).toInstant());
    }

    public LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(zoneId).toLocalDate();
    }

    public Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(zoneId).toInstant());
    }
}
