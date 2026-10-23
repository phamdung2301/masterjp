package com.masterjp.service;

import com.masterjp.domain.AiGrade;
import com.masterjp.domain.Kanji;
import com.masterjp.domain.KanjiProgress;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordSection;
import com.masterjp.domain.WrongAnswer;
import com.masterjp.dto.ProgressDtos;
import com.masterjp.dto.ReviewDtos;
import com.masterjp.repository.AiGradeRepository;
import com.masterjp.repository.KanjiRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WordProgressRepository;
import com.masterjp.repository.WrongAnswerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F-043: unit test tron queue (Mockito, khong can DB):
 * thu tu wrong -> due theo overdueDays, counts chua cat limit,
 * estimatedMinutes dung cong thuc ceil((wrong*15 + vocab*8 + kanji*8)/60).
 */
@ExtendWith(MockitoExtension.class)
class ReviewQueueServiceTest {

    @Mock
    private WrongAnswerRepository wrongAnswerRepository;
    @Mock
    private WordProgressRepository wordProgressRepository;
    @Mock
    private VocabWordRepository vocabWordRepository;
    @Mock
    private AiGradeRepository aiGradeRepository;
    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private ProgressService progressService;
    @Mock
    private KanjiProgressService kanjiProgressService;
    @InjectMocks
    private ReviewQueueService reviewQueueService;

    private VocabWord word(String id, String kana, String meaningVi) {
        VocabWord w = new VocabWord();
        w.setId(id);
        w.setKana(kana);
        w.setKanji(kana);
        w.setRomaji("r-" + id);
        w.setMeaningVi(meaningVi);
        w.setAudioUrl("/audio/" + id + ".mp3");
        return w;
    }

    private ProgressDtos.DueWordDto dueWord(String id, long overdueDays) {
        return new ProgressDtos.DueWordDto(id, "l1", 1, WordSection.VOCAB.name(),
                "かな" + id, "漢字" + id, "romaji" + id, "nghĩa " + id,
                "danh từ", null, null, "/audio/" + id + ".mp3", 1, "LEARNING", overdueDays);
    }

    private WrongAnswer wrongVocab(String id, String wordId, Date createdAt) {
        WrongAnswer w = new WrongAnswer();
        w.setId(id);
        w.setUserId("u1");
        w.setKind(WrongAnswer.KIND_VOCAB);
        w.setWordId(wordId);
        w.setCreatedAt(createdAt);
        return w;
    }

    @Test
    void queue_tronDungThuTu_countsVaEstimatedMinutesDungCongThuc() {
        // wrong: 2 item (ai_sentence moi hon vocab)
        WrongAnswer waVocab = wrongVocab("wa1", "w1", new Date(1_700_000_000_000L));
        WrongAnswer waAi = new WrongAnswer();
        waAi.setId("wa2");
        waAi.setUserId("u1");
        waAi.setKind(WrongAnswer.KIND_AI_SENTENCE);
        waAi.setAiGradeId("g1");
        waAi.setCreatedAt(new Date(1_800_000_000_000L));
        when(wrongAnswerRepository.findByUserIdAndResolvedFalseOrderByCreatedAtDesc(
                eq("u1"), any())).thenReturn(List.of(waAi, waVocab));

        when(vocabWordRepository.findByIdInAndActiveTrue(List.of("w1")))
                .thenReturn(List.of(word("w1", "たべる", "ăn")));

        AiGrade grade = new AiGrade();
        grade.setId("g1");
        grade.setSentence("毎日日本語を勉強します。");
        grade.setCorrected("毎日日本語を勉強しています。");
        grade.setErrors(List.of(new AiGrade.ErrorItem("勉強します", "Thì sai", "勉強しています")));
        when(aiGradeRepository.findById("g1")).thenReturn(Optional.of(grade));

        // due: vocab overdue 5, 2, 1; kanji overdue 3
        when(progressService.dueWords("u1", 50)).thenReturn(List.of(
                dueWord("w2", 5), dueWord("w3", 2), dueWord("w4", 1)));
        KanjiProgress kp = new KanjiProgress();
        kp.setUserId("u1");
        kp.setCharacter("日");
        when(kanjiProgressService.dueKanji("u1", 50))
                .thenReturn(List.of(new KanjiProgressService.DueKanji(kp, 3)));
        Kanji k = new Kanji();
        k.setId("日");
        k.setCharacter("日");
        k.setMeaningVi("mặt trời, ngày");
        k.setOnYomi(List.of("ニチ"));
        k.setKunYomi(List.of("ひ"));
        when(kanjiRepository.findAllById(List.of("日"))).thenReturn(List.of(k));

        // counts chua cat limit
        when(wrongAnswerRepository.countByUserIdAndResolvedFalse("u1")).thenReturn(2L);
        when(wordProgressRepository.countByUserIdAndNextReviewAtLessThanEqual(eq("u1"), any()))
                .thenReturn(3L);
        when(kanjiProgressService.countDue("u1")).thenReturn(1L);

        ReviewDtos.QueueResponse res = reviewQueueService.queue("u1", 50);

        // Thu tu: wrong (moi nhat truoc) -> due theo overdueDays giam dan
        List<ReviewDtos.QueueItemDto> items = res.items();
        assertEquals(6, items.size());
        assertEquals("wa2", items.get(0).id()); // wrong ai_sentence moi nhat
        assertEquals("ai_sentence", items.get(0).refKind());
        assertEquals("wa1", items.get(1).id()); // wrong vocab
        assertEquals("w1", items.get(1).wordId());
        assertEquals("たべる", items.get(1).kana());
        assertEquals("w2", items.get(2).wordId()); // overdue 5
        assertEquals("日", items.get(3).character()); // overdue 3
        assertEquals(List.of("ニチ"), items.get(3).onYomi());
        assertEquals("mặt trời, ngày", items.get(3).meaningVi()); // kanji item phai co meaningVi (join tu collection kanji)
        assertEquals("w3", items.get(4).wordId()); // overdue 2
        assertEquals("w4", items.get(5).wordId()); // overdue 1

        ReviewDtos.QueueCountsDto counts = res.counts();
        assertEquals(3, counts.vocab());
        assertEquals(1, counts.kanji());
        assertEquals(2, counts.wrong());
        assertEquals(6, counts.total());
        // ceil((2*15 + 3*8 + 1*8)/60) = ceil(62/60) = 2
        assertEquals(2, res.estimatedMinutes());
    }

    @Test
    void queue_limitCatItems_nhungCountsGiuyen() {
        when(wrongAnswerRepository.findByUserIdAndResolvedFalseOrderByCreatedAtDesc(
                eq("u1"), any())).thenReturn(List.of());
        when(progressService.dueWords("u1", 2)).thenReturn(List.of(
                dueWord("w2", 5), dueWord("w3", 2), dueWord("w4", 1)));
        when(kanjiProgressService.dueKanji("u1", 2)).thenReturn(List.of());
        when(wrongAnswerRepository.countByUserIdAndResolvedFalse("u1")).thenReturn(0L);
        when(wordProgressRepository.countByUserIdAndNextReviewAtLessThanEqual(eq("u1"), any()))
                .thenReturn(3L);
        when(kanjiProgressService.countDue("u1")).thenReturn(0L);

        ReviewDtos.QueueResponse res = reviewQueueService.queue("u1", 2);

        assertEquals(2, res.items().size());
        assertEquals("w2", res.items().get(0).wordId());
        assertEquals(3, res.counts().vocab());
        assertEquals(3, res.counts().total());
    }

    @Test
    void queue_rong() {
        when(wrongAnswerRepository.findByUserIdAndResolvedFalseOrderByCreatedAtDesc(
                eq("u1"), any())).thenReturn(List.of());
        when(progressService.dueWords("u1", 50)).thenReturn(List.of());
        when(kanjiProgressService.dueKanji("u1", 50)).thenReturn(List.of());
        when(wrongAnswerRepository.countByUserIdAndResolvedFalse("u1")).thenReturn(0L);
        when(wordProgressRepository.countByUserIdAndNextReviewAtLessThanEqual(eq("u1"), any()))
                .thenReturn(0L);
        when(kanjiProgressService.countDue("u1")).thenReturn(0L);

        ReviewDtos.QueueResponse res = reviewQueueService.queue("u1", null);

        assertTrue(res.items().isEmpty());
        assertEquals(0, res.counts().total());
        assertEquals(0, res.estimatedMinutes());
    }

    @Test
    void queue_wrongVocabTuBiXoaMem_boQuaItem() {
        when(wrongAnswerRepository.findByUserIdAndResolvedFalseOrderByCreatedAtDesc(
                eq("u1"), any())).thenReturn(List.of(wrongVocab("wa9", "gone", new Date())));
        when(vocabWordRepository.findByIdInAndActiveTrue(List.of("gone"))).thenReturn(List.of());
        when(progressService.dueWords("u1", 50)).thenReturn(List.of());
        when(kanjiProgressService.dueKanji("u1", 50)).thenReturn(List.of());
        when(wrongAnswerRepository.countByUserIdAndResolvedFalse("u1")).thenReturn(1L);
        when(wordProgressRepository.countByUserIdAndNextReviewAtLessThanEqual(eq("u1"), any()))
                .thenReturn(0L);
        when(kanjiProgressService.countDue("u1")).thenReturn(0L);

        ReviewDtos.QueueResponse res = reviewQueueService.queue("u1", 50);

        assertTrue(res.items().isEmpty());
        assertEquals(1, res.counts().wrong());
        assertNull(res.items().stream().findFirst().orElse(null));
    }
}
