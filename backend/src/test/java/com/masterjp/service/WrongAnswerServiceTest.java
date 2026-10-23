package com.masterjp.service;

import com.masterjp.domain.StudySession;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WrongAnswer;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WrongAnswerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-043: unit test wrong_answers (Mockito, khong can DB):
 * dedupe unresolved, retention cap 100, resolve idempotent + 404.
 */
@ExtendWith(MockitoExtension.class)
class WrongAnswerServiceTest {

    @Mock
    private WrongAnswerRepository wrongAnswerRepository;
    @Mock
    private VocabWordRepository vocabWordRepository;
    @InjectMocks
    private WrongAnswerService wrongAnswerService;

    private VocabWord word(String id) {
        VocabWord w = new VocabWord();
        w.setId(id);
        return w;
    }

    @Test
    void recordFromSession_tuDung_khongGhi() {
        wrongAnswerService.recordFromSession("u1", "s1",
                List.of(new StudySession.SessionItem("w1", true, 100L)));

        verify(wrongAnswerRepository, never()).save(any());
    }

    @Test
    void recordFromSession_tuSaiMoi_ghi1BanGhi() {
        when(vocabWordRepository.findByIdInAndActiveTrue(List.of("w1")))
                .thenReturn(List.of(word("w1")));
        when(wrongAnswerRepository.existsByUserIdAndKindAndWordIdAndResolvedFalse(
                "u1", "vocab", "w1")).thenReturn(false);
        when(wrongAnswerRepository.countByUserIdAndResolvedFalse("u1")).thenReturn(1L);

        wrongAnswerService.recordFromSession("u1", "s1",
                List.of(new StudySession.SessionItem("w1", false, 100L)));

        ArgumentCaptor<WrongAnswer> captor = ArgumentCaptor.forClass(WrongAnswer.class);
        verify(wrongAnswerRepository).save(captor.capture());
        WrongAnswer saved = captor.getValue();
        assertEquals("u1", saved.getUserId());
        assertEquals("vocab", saved.getKind());
        assertEquals("w1", saved.getWordId());
        assertEquals("s1", saved.getSessionId());
    }

    @Test
    void recordFromSession_daCoUnresolved_khongGhiTrung() {
        when(vocabWordRepository.findByIdInAndActiveTrue(List.of("w1")))
                .thenReturn(List.of(word("w1")));
        when(wrongAnswerRepository.existsByUserIdAndKindAndWordIdAndResolvedFalse(
                "u1", "vocab", "w1")).thenReturn(true);

        wrongAnswerService.recordFromSession("u1", "s1",
                List.of(new StudySession.SessionItem("w1", false, 100L)));

        verify(wrongAnswerRepository, never()).save(any());
    }

    @Test
    void recordFromSession_boQuaWordIdKhongPhaiTuVung() {
        // "shadow:..." (F-041) va ky tu kanji khong phai id tu vung that.
        when(vocabWordRepository.findByIdInAndActiveTrue(List.of("shadow:sh01", "日")))
                .thenReturn(List.of());

        wrongAnswerService.recordFromSession("u1", "s1", List.of(
                new StudySession.SessionItem("shadow:sh01", false, 100L),
                new StudySession.SessionItem("日", false, 100L)));

        verify(wrongAnswerRepository, never()).save(any());
    }

    @Test
    void recordFromSession_vuot100Unresolved_xoaCuNhat() {
        when(vocabWordRepository.findByIdInAndActiveTrue(List.of("w9")))
                .thenReturn(List.of(word("w9")));
        when(wrongAnswerRepository.existsByUserIdAndKindAndWordIdAndResolvedFalse(
                "u1", "vocab", "w9")).thenReturn(false);
        when(wrongAnswerRepository.countByUserIdAndResolvedFalse("u1")).thenReturn(101L);
        WrongAnswer oldest = new WrongAnswer();
        oldest.setId("old1");
        when(wrongAnswerRepository.findByUserIdAndResolvedFalseOrderByCreatedAtAsc(
                eq("u1"), any())).thenReturn(List.of(oldest));

        wrongAnswerService.recordFromSession("u1", "s1",
                List.of(new StudySession.SessionItem("w9", false, 100L)));

        verify(wrongAnswerRepository).deleteAll(List.of(oldest));
    }

    @Test
    void recordFromAiGrade_khongDedupe_ghiMoi() {
        when(wrongAnswerRepository.countByUserIdAndResolvedFalse("u1")).thenReturn(1L);

        wrongAnswerService.recordFromAiGrade("u1", "g1");

        ArgumentCaptor<WrongAnswer> captor = ArgumentCaptor.forClass(WrongAnswer.class);
        verify(wrongAnswerRepository).save(captor.capture());
        assertEquals("ai_sentence", captor.getValue().getKind());
        assertEquals("g1", captor.getValue().getAiGradeId());
    }

    @Test
    void resolve_thanhCong() {
        WrongAnswer wa = new WrongAnswer();
        wa.setId("wa1");
        wa.setUserId("u1");
        when(wrongAnswerRepository.findById("wa1")).thenReturn(Optional.of(wa));

        var res = wrongAnswerService.resolve("u1", "wa1");

        assertEquals("wa1", res.id());
        assertTrue(res.resolved());
        verify(wrongAnswerRepository).save(wa);
        assertTrue(wa.isResolved());
    }

    @Test
    void resolve_daResolve_idempotent_khongSaveLai() {
        WrongAnswer wa = new WrongAnswer();
        wa.setId("wa1");
        wa.setUserId("u1");
        wa.setResolved(true);
        when(wrongAnswerRepository.findById("wa1")).thenReturn(Optional.of(wa));

        var res = wrongAnswerService.resolve("u1", "wa1");

        assertTrue(res.resolved());
        verify(wrongAnswerRepository, never()).save(any());
    }

    @Test
    void resolve_idCuaUserKhac_404() {
        WrongAnswer wa = new WrongAnswer();
        wa.setId("wa1");
        wa.setUserId("other");
        when(wrongAnswerRepository.findById("wa1")).thenReturn(Optional.of(wa));

        ApiException ex = assertThrows(ApiException.class,
                () -> wrongAnswerService.resolve("u1", "wa1"));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void resolve_idKhongTonTai_404() {
        when(wrongAnswerRepository.findById("nope")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> wrongAnswerService.resolve("u1", "nope"));
        assertEquals("NOT_FOUND", ex.getCode());
    }
}
