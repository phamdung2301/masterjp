package com.masterjp.service;

import com.masterjp.domain.KanjiProgress;
import com.masterjp.domain.ProgressStatus;
import com.masterjp.dto.ProgressDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.KanjiProgressRepository;
import com.masterjp.repository.KanjiRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-043: unit test SM-2 cho kanji (Mockito, khong can DB).
 * Kiem chung kanji TAI SU DUNG Sm2Calculator (khong duplicate cong thuc):
 * cung chuyen trang thai nhu tu vung (repetitions/interval/easeFactor/status).
 */
@ExtendWith(MockitoExtension.class)
class KanjiProgressServiceTest {

    @Mock
    private KanjiProgressRepository kanjiProgressRepository;
    @Mock
    private KanjiRepository kanjiRepository;
    @InjectMocks
    private KanjiProgressService kanjiProgressService;

    private void stubKanjiExists(String character) {
        com.masterjp.domain.Kanji k = new com.masterjp.domain.Kanji();
        k.setId(character);
        k.setCharacter(character);
        when(kanjiRepository.findByCharacter(character)).thenReturn(Optional.of(k));
    }

    @Test
    void answerKanji_moiDung_repetitions1_interval1_giongTuVung() {
        stubKanjiExists("日");
        when(kanjiProgressRepository.findByUserIdAndCharacter("u1", "日"))
                .thenReturn(Optional.empty());

        ProgressDtos.AnswerResponse res = kanjiProgressService.answerKanji("u1", "日", true);

        ArgumentCaptor<KanjiProgress> captor = ArgumentCaptor.forClass(KanjiProgress.class);
        verify(kanjiProgressRepository).save(captor.capture());
        KanjiProgress saved = captor.getValue();
        assertEquals("u1", saved.getUserId());
        assertEquals("日", saved.getCharacter());
        assertEquals(1, saved.getRepetitions());
        assertEquals(1, saved.getIntervalDays());
        assertEquals(ProgressStatus.LEARNING, saved.getStatus());
        assertNotNull(saved.getNextReviewAt());
        assertEquals("LEARNING", res.status());
        assertEquals(1, res.intervalDays());
        assertEquals(1, res.repetitions());
    }

    @Test
    void answerKanji_sai_truEaseFactor_resetRepetitions() {
        stubKanjiExists("月");
        KanjiProgress existing = new KanjiProgress();
        existing.setUserId("u1");
        existing.setCharacter("月");
        existing.setRepetitions(2);
        existing.setIntervalDays(6);
        when(kanjiProgressRepository.findByUserIdAndCharacter("u1", "月"))
                .thenReturn(Optional.of(existing));

        kanjiProgressService.answerKanji("u1", "月", false);

        ArgumentCaptor<KanjiProgress> captor = ArgumentCaptor.forClass(KanjiProgress.class);
        verify(kanjiProgressRepository).save(captor.capture());
        KanjiProgress saved = captor.getValue();
        assertEquals(0, saved.getRepetitions());
        assertEquals(1, saved.getIntervalDays());
        assertEquals(1, saved.getLapses());
        assertEquals(2.3, saved.getEaseFactor(), 0.0001);
    }

    @Test
    void answerKanji_kyTuKhongTonTai_404() {
        when(kanjiRepository.findByCharacter("〄")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> kanjiProgressService.answerKanji("u1", "〄", true));
        assertEquals("KANJI_NOT_FOUND", ex.getCode());
        verify(kanjiProgressRepository, never()).save(any());
    }
}
