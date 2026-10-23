package com.masterjp.service;

import com.masterjp.domain.AiUsage;
import com.masterjp.dto.AiDtos;
import com.masterjp.exception.QuotaExceededException;
import com.masterjp.util.AppTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho AiRateLimiter (Phase B — Sensei AI).
 * Mock MongoTemplate.findAndModify/findOne — khong can MongoDB that.
 * AppTime duoc mock de ngay hom nay co dinh (2026-10-22, Asia/Ho_Chi_Minh).
 * Chay bang: mvn test -Dtest=AiRateLimiterTest
 */
@ExtendWith(MockitoExtension.class)
class AiRateLimiterTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AppTime appTime;

    /**
     * Tao limiter moi cho moi test (thay vi @BeforeEach): trainh
     * UnnecessaryStubbingException o test chi kiem tra resolveLimit
     * (khong goi consume/readQuota -> khong dung stub appTime).
     */
    private AiRateLimiter newLimiter(MockEnvironment env) {
        when(appTime.today()).thenReturn(LocalDate.of(2026, 10, 22));
        when(appTime.zoneId()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        return new AiRateLimiter(mongoTemplate, appTime, env);
    }

    private AiUsage usageDoc(long used) {
        AiUsage doc = new AiUsage();
        doc.setUserId("u1");
        doc.setDate("2026-10-22");
        doc.setUsed(used);
        return doc;
    }

    private void stubFindAndModify(AiUsage doc) {
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AiUsage.class)))
                .thenReturn(doc);
    }

    @Test
    void consume_traQuotaSauKhiTru_used1_remaining49() {
        AiRateLimiter limiter = newLimiter(new MockEnvironment());
        stubFindAndModify(usageDoc(1));

        AiDtos.QuotaResponse q = limiter.consume("u1");

        assertEquals(50, q.limit());
        assertEquals(1, q.used());
        assertEquals(49, q.remaining());
        assertEquals("2026-10-23T00:00:00+07:00", q.resetAt());
        // resetAt phai mang offset +07:00 (Asia/Ho_Chi_Minh), khong phai UTC
        assertTrue(q.resetAt().contains("+07:00"));
    }

    @Test
    void consume_findAndModifyTraNull_tinhUsedBang1() {
        AiRateLimiter limiter = newLimiter(new MockEnvironment());
        stubFindAndModify(null);

        AiDtos.QuotaResponse q = limiter.consume("u1");

        assertEquals(1, q.used());
        assertEquals(49, q.remaining());
    }

    @Test
    void consume_dungHetQuota_remaining0_khongNem() {
        AiRateLimiter limiter = newLimiter(new MockEnvironment());
        stubFindAndModify(usageDoc(50)); // used == limit: van OK, con 0 luot

        AiDtos.QuotaResponse q = limiter.consume("u1");

        assertEquals(50, q.used());
        assertEquals(0, q.remaining());
    }

    @Test
    void consume_vuotQuota_nemQuotaExceededException_429_resetAt() {
        AiRateLimiter limiter = newLimiter(new MockEnvironment());
        stubFindAndModify(usageDoc(51));

        QuotaExceededException e = assertThrows(QuotaExceededException.class,
                () -> limiter.consume("u1"));

        assertEquals("DAILY_LIMIT", e.getCode());
        assertEquals(429, e.getStatus().value());
        assertEquals("2026-10-23T00:00:00+07:00", e.getResetAt());
        assertTrue(e.getResetAt().contains("+07:00"));
    }

    @Test
    void consume_goiFindAndModifyMotLan_atomicCheckAndIncrement() {
        AiRateLimiter limiter = newLimiter(new MockEnvironment());
        stubFindAndModify(usageDoc(2));

        limiter.consume("u1");

        verify(mongoTemplate).findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AiUsage.class));
    }

    @Test
    void readQuota_khongTruQuota_chiDoc() {
        AiUsage doc = usageDoc(10);
        AiRateLimiter limiter = newLimiter(new MockEnvironment());
        when(mongoTemplate.findOne(any(Query.class), eq(AiUsage.class))).thenReturn(doc);

        AiDtos.QuotaResponse q = limiter.readQuota("u1");

        assertEquals(50, q.limit());
        assertEquals(10, q.used());
        assertEquals(40, q.remaining());
        assertEquals("2026-10-23T00:00:00+07:00", q.resetAt());
        // doc quota KHONG duoc goi findAndModify (khong tru quota)
        verify(mongoTemplate, never()).findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(AiUsage.class));
    }

    @Test
    void readQuota_chuaDungLanNao_used0_remainingDu() {
        AiRateLimiter limiter = newLimiter(new MockEnvironment());
        when(mongoTemplate.findOne(any(Query.class), eq(AiUsage.class))).thenReturn(null);

        AiDtos.QuotaResponse q = limiter.readQuota("u1");

        assertEquals(0, q.used());
        assertEquals(50, q.remaining());
    }

    @Test
    void limitTuEnv_duocApDung() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("AI_DAILY_LIMIT_PER_USER", "3");
        AiRateLimiter custom = newLimiter(env);

        assertEquals(3, custom.getDailyLimit());

        stubFindAndModify(usageDoc(4));
        assertThrows(QuotaExceededException.class, () -> custom.consume("u1"));
    }

    @Test
    void limitEnvKhongHopLe_roiVeDefault50() {
        MockEnvironment bad = new MockEnvironment();
        bad.setProperty("AI_DAILY_LIMIT_PER_USER", "abc");
        assertEquals(50, new AiRateLimiter(mongoTemplate, appTime, bad).getDailyLimit());

        MockEnvironment negative = new MockEnvironment();
        negative.setProperty("AI_DAILY_LIMIT_PER_USER", "-5");
        assertEquals(50, new AiRateLimiter(mongoTemplate, appTime, negative).getDailyLimit());

        assertEquals(50, new AiRateLimiter(mongoTemplate, appTime, null).getDailyLimit());
    }
}
