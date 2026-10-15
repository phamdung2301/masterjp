package com.masterjp.service;

import com.masterjp.domain.Kanji;
import com.masterjp.domain.Lesson;
import com.masterjp.domain.VocabWord;
import com.masterjp.dto.KanjiDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.KanjiRepository;
import com.masterjp.repository.LessonRepository;
import com.masterjp.repository.VocabWordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** F-B (Feature Pack 2): noi dung kanji public read (GUEST khong can auth). */
@Service
public class KanjiService {

    private final KanjiRepository kanjiRepository;
    private final VocabWordRepository vocabWordRepository;
    private final LessonRepository lessonRepository;

    public KanjiService(KanjiRepository kanjiRepository,
                        VocabWordRepository vocabWordRepository,
                        LessonRepository lessonRepository) {
        this.kanjiRepository = kanjiRepository;
        this.vocabWordRepository = vocabWordRepository;
        this.lessonRepository = lessonRepository;
    }

    /**
     * GET /api/kanji?lessonNo=&jlpt= — ca 2 param optional; khong param nao
     * -> tat ca chu, luon sap theo so net tang dan.
     */
    public List<KanjiDtos.KanjiDto> listKanji(Integer lessonNo, String jlpt) {
        String jlptNorm = jlpt == null || jlpt.isBlank() ? null : jlpt.trim().toUpperCase();
        List<Kanji> kanji;
        if (lessonNo != null && jlptNorm != null) {
            kanji = kanjiRepository.findByLessonNosContainingAndJlptOrderByStrokesAsc(lessonNo, jlptNorm);
        } else if (lessonNo != null) {
            kanji = kanjiRepository.findByLessonNosContainingOrderByStrokesAsc(lessonNo);
        } else if (jlptNorm != null) {
            kanji = kanjiRepository.findByJlptOrderByStrokesAsc(jlptNorm);
        } else {
            kanji = kanjiRepository.findAllByOrderByStrokesAsc();
        }
        return kanji.stream().map(this::toDto).toList();
    }

    /**
     * GET /api/kanji/{character} — chi tiet chu + toi da 50 tu chua chu do
     * (sap theo lessonNo, roi orderInLesson). studyStatus de null (public read).
     */
    public KanjiDtos.KanjiDetailDto getDetail(String character) {
        Kanji kanji = kanjiRepository.findByCharacter(character)
                .orElseThrow(() -> new ApiException("KANJI_NOT_FOUND",
                        "Không tìm thấy chữ kanji này.", HttpStatus.NOT_FOUND));

        Map<String, Integer> lessonNoById = lessonRepository.findAll().stream()
                .collect(Collectors.toMap(Lesson::getId, Lesson::getLessonNo, (a, b) -> a));

        List<WordWithLessonNo> words = vocabWordRepository
                .findByKanjiSubstringAndActiveTrue(java.util.regex.Pattern.quote(character)).stream()
                .map(w -> new WordWithLessonNo(w,
                        lessonNoById.getOrDefault(w.getLessonId(), Integer.MAX_VALUE)))
                .sorted(Comparator.comparingInt(WordWithLessonNo::lessonNo)
                        .thenComparingInt(a -> a.word().getOrderInLesson()))
                .limit(50)
                .toList();

        return new KanjiDtos.KanjiDetailDto(
                kanji.getCharacter(), kanji.getOnYomi(), kanji.getKunYomi(),
                kanji.getMeaningVi(), kanji.getStrokes(), kanji.getJlpt(),
                kanji.getLessonNos(),
                words.stream().map(a -> WordMapper.toDto(a.word(), null)).toList());
    }

    private KanjiDtos.KanjiDto toDto(Kanji k) {
        return new KanjiDtos.KanjiDto(k.getCharacter(), k.getOnYomi(), k.getKunYomi(),
                k.getMeaningVi(), k.getStrokes(), k.getJlpt(), k.getLessonNos());
    }

    /** Record noi bo de sap xep tu theo lessonNo truoc khi map sang DTO. */
    private record WordWithLessonNo(VocabWord word, int lessonNo) {
    }
}
