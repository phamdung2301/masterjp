package com.masterjp.service;

import com.masterjp.domain.DailyUsage;
import com.masterjp.domain.Plan;
import com.masterjp.domain.User;
import com.masterjp.exception.FreemiumLimitException;
import com.masterjp.repository.UserRepository;
import com.masterjp.util.AppTime;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho FreemiumLimitService (Phase D — F-045: 20 từ mới/ngày cho FREE).
 * Mock MongoTemplate.findAndModify — không cần MongoDB thật.
 * AppTime được mock để "hôm nay" cố định (2026-10-22, Asia/Ho_Chi_Minh).
 * Chay bang: mvn test -Dtest=FreemiumLimitServiceTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FreemiumLimitService — 20 từ mới/ngày cho FREE")
class FreemiumLimitServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private AppTime appTime;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlanExpiryService planExpiryService;

    /**
     * Tạo service mới cho mỗi test (tránh UnnecessaryStubbingException):
     * stub appTime/user ở đây vì mọi test consumeNewWord đều cần.
     */
    private FreemiumLimitService newService(User user, DailyUsage usageDoc) {
        when(appTime.today()).thenReturn(TODAY);
        // zoneId chỉ dùng khi ném 429 (tính resetAt) -> lenient cho các test happy-path
        lenient().when(appTime.zoneId()).thenReturn(ZONE);
        when(userRepository.findById("u1")).thenReturn(Optional.ofNullable(user));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(DailyUsage.class)))
                .thenReturn(usageDoc);
        return new FreemiumLimitService(mongoTemplate, appTime, userRepository, planExpiryService);
    }

    private User freeUser() {
        User u = new User();
        u.setId("u1");
        u.setEmail("free@example.com");
        u.setPlan(Plan.FREE);
        return u;
    }

    private User premiumUser() {
        User u = freeUser();
        u.setPlan(Plan.PREMIUM);
        return u;
    }

    private DailyUsage usageDoc(long newWordCount) {
        DailyUsage d = new DailyUsage();
        d.setUserId("u1");
        d.setDate("2026-10-22");
        d.setNewWordCount(newWordCount);
        return d;
    }

    // ================= Happy path =================

    @Test
    @DisplayName("FREE học dưới 20 từ: trả về count sau khi trừ, không ném")
    void consume_freeDuoi20_traVeCount() {
        FreemiumLimitService svc = newService(freeUser(), usageDoc(5));

        long count = svc.consumeNewWord("u1");

        assertEquals(5, count);
    }

    @Test
    @DisplayName("FREE đúng từ thứ 20: vẫn OK (chỉ ném khi > 20)")
    void consume_freeDung20_vanOK() {
        FreemiumLimitService svc = newService(freeUser(), usageDoc(20));

        long count = svc.consumeNewWord("u1");

        assertEquals(20, count);
    }

    @Test
    @DisplayName("findAndModify trả null (ngày đầu tiên): tính count = 1")
    void consume_findAndModifyNull_tinh1() {
        FreemiumLimitService svc = newService(freeUser(), null);

        assertEquals(1, svc.consumeNewWord("u1"));
    }

    // ================= Vượt trần =================

    @Test
    @DisplayName("FREE từ thứ 21: ném 429 FREEMIUM_LIMIT, resetAt 00:00 hôm sau +07:00")
    void consume_freeVuot20_nem429() {
        FreemiumLimitService svc = newService(freeUser(), usageDoc(21));

        FreemiumLimitException e = assertThrows(FreemiumLimitException.class,
                () -> svc.consumeNewWord("u1"));

        assertEquals("FREEMIUM_LIMIT", e.getCode());
        assertEquals(429, e.getStatus().value());
        assertEquals(20, e.getLimit());
        assertEquals("2026-10-23T00:00:00+07:00", e.getResetAt());
        assertTrue(e.getResetAt().contains("+07:00"),
                "resetAt phải mang offset Asia/Ho_Chi_Minh, không phải UTC");
    }

    // ================= PREMIUM bypass =================

    @Test
    @DisplayName("PREMIUM: không giới hạn — count 999 vẫn qua")
    void consume_premium_bypassKhongNem() {
        User premium = premiumUser();
        FreemiumLimitService svc = newService(premium, usageDoc(999));
        when(planExpiryService.applyIfExpired(premium)).thenReturn(false);

        long count = svc.consumeNewWord("u1");

        assertEquals(999, count);
    }

    @Test
    @DisplayName("User không tồn tại trong DB: tính như FREE (fail-closed)")
    void consume_userKhongTonTai_tinhNhuFree() {
        FreemiumLimitService svc = new FreemiumLimitService(
                mongoTemplate, appTime, userRepository, planExpiryService);
        when(appTime.today()).thenReturn(TODAY);
        when(appTime.zoneId()).thenReturn(ZONE);
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(DailyUsage.class)))
                .thenReturn(usageDoc(21));

        assertThrows(FreemiumLimitException.class, () -> svc.consumeNewWord("ghost"));
    }

    @Test
    @DisplayName("Premium vừa hết hạn: applyIfExpired hạ về FREE rồi mới enforce")
    void consume_premiumVuaHetHan_biHaVeFree_vaEnforce() {
        User expiredPremium = premiumUser();
        FreemiumLimitService svc = newService(expiredPremium, usageDoc(21));
        // applyIfExpired mutate user -> FREE và trả true (caller phải save)
        when(planExpiryService.applyIfExpired(expiredPremium)).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setPlan(Plan.FREE);
            u.setPlanExpiresAt(null);
            return true;
        });

        assertThrows(FreemiumLimitException.class, () -> svc.consumeNewWord("u1"));

        verify(userRepository).save(expiredPremium);
        assertEquals(Plan.FREE, expiredPremium.getPlan());
    }

    // ================= Atomicity: inc-trước-check-sau =================

    @Test
    @DisplayName("findAndModify: upsert + returnNew — 2 request song song không lọt khe 20->21")
    void consume_findAndModify_upsertReturnNew_atomic() {
        FreemiumLimitService svc = newService(freeUser(), usageDoc(3));

        svc.consumeNewWord("u1");

        ArgumentCaptor<FindAndModifyOptions> optCaptor =
                ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(any(Query.class), any(Update.class),
                optCaptor.capture(), eq(DailyUsage.class));
        FindAndModifyOptions opts = optCaptor.getValue();
        assertTrue(opts.isUpsert(), "phải upsert để ngày mới tự tạo doc");
        assertTrue(opts.isReturnNew(), "phải returnNew để check trên giá trị SAU khi trừ");
    }

    @Test
    @DisplayName("Update dùng $inc newWordCount: 1 — trừ nguyên tử phía MongoDB")
    void consume_update_dungIncNewWordCount() {
        FreemiumLimitService svc = newService(freeUser(), usageDoc(3));

        svc.consumeNewWord("u1");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(DailyUsage.class));
        Document inc = (Document) updateCaptor.getValue().getUpdateObject().get("$inc");
        assertEquals(1, inc.getInteger("newWordCount"));
    }

    @Test
    @DisplayName("Query đúng userId + date hôm nay theo Asia/Ho_Chi_Minh")
    void consume_query_dungUserIdVaDate() {
        FreemiumLimitService svc = newService(freeUser(), usageDoc(3));

        svc.consumeNewWord("u1");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), any(Update.class),
                any(FindAndModifyOptions.class), eq(DailyUsage.class));
        Document q = queryCaptor.getValue().getQueryObject();
        assertEquals("u1", q.getString("userId"));
        assertEquals("2026-10-22", q.getString("date"));
    }

    // ================= Reset theo ngày =================

    @Test
    @DisplayName("Sang ngày mới: query date đổi -> đếm lại từ đầu (limit reset)")
    void consume_sangNgayMoi_resetDem() {
        FreemiumLimitService svc = newService(freeUser(), usageDoc(1));
        when(appTime.today()).thenReturn(LocalDate.of(2026, 10, 23)); // sang hôm sau

        long count = svc.consumeNewWord("u1");

        assertEquals(1, count);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), any(Update.class),
                any(FindAndModifyOptions.class), eq(DailyUsage.class));
        assertEquals("2026-10-23",
                queryCaptor.getValue().getQueryObject().getString("date"));
    }

    @Test
    @DisplayName("Mỗi lần consume: đúng 1 findAndModify + 1 lần resolve plan")
    void consume_moiLanGoi_chiMotFindAndModify() {
        FreemiumLimitService svc = newService(freeUser(), usageDoc(2));

        svc.consumeNewWord("u1");

        verify(mongoTemplate, org.mockito.Mockito.times(1)).findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(DailyUsage.class));
        // plan luôn được resolve qua applyIfExpired (mock trả false) rồi mới check trần
        verify(userRepository, org.mockito.Mockito.times(1)).findById("u1");
        verify(planExpiryService, org.mockito.Mockito.times(1)).applyIfExpired(any(User.class));
        // FREE không hết hạn -> không save lại user
        verify(userRepository, never()).save(any(User.class));
    }
}
