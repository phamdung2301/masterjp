package com.masterjp.service;

import com.masterjp.domain.Lesson;
import com.masterjp.domain.VocabWord;
import com.masterjp.dto.WordDtos;
import com.masterjp.repository.LessonRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.util.VietnameseUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tim kiem tu dien (F-012): kana/kanji/romaji/nghia Viet, go khong dau van ra.
 * Thu tu uu tien: khop chinh xac kana/kanji -> prefix -> text search (nghia Viet
 * chuan hoa). Toi da 50 ket qua.
 */
@Service
public class WordService {

    private final VocabWordRepository vocabWordRepository;
    private final LessonRepository lessonRepository;
    private final MongoTemplate mongoTemplate;

    public WordService(VocabWordRepository vocabWordRepository,
                       LessonRepository lessonRepository,
                       MongoTemplate mongoTemplate) {
        this.vocabWordRepository = vocabWordRepository;
        this.lessonRepository = lessonRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<WordDtos.WordSearchDto> search(String q) {
        String raw = q == null ? "" : q.trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        Map<String, VocabWord> merged = new LinkedHashMap<>();

        // 1. Khop chinh xac kana / kanji
        for (VocabWord w : vocabWordRepository.findByKana(raw)) {
            if (w.isActive()) {
                merged.putIfAbsent(w.getId(), w);
            }
        }
        for (VocabWord w : vocabWordRepository.findByKanji(raw)) {
            if (w.isActive()) {
                merged.putIfAbsent(w.getId(), w);
            }
        }

        // 2. Prefix tren kana / kanji / romaji (khong phan biet hoa thuong)
        String prefix = "^" + Pattern.quote(raw);
        Criteria prefixCriteria = new Criteria().andOperator(
                new Criteria().orOperator(
                        Criteria.where("kana").regex(prefix, "i"),
                        Criteria.where("kanji").regex(prefix, "i"),
                        Criteria.where("romaji").regex(prefix, "i")),
                Criteria.where("active").is(true));
        Query prefixQuery = new Query(prefixCriteria).limit(50);
        for (VocabWord w : mongoTemplate.find(prefixQuery, VocabWord.class)) {
            merged.putIfAbsent(w.getId(), w);
        }

        // 3. Text search tren chuoi chuan hoa (nghia Viet bo dau)
        String normalized = VietnameseUtils.normalize(raw);
        if (!normalized.isEmpty()) {
            TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(normalized);
            TextQuery textQuery = TextQuery.queryText(textCriteria).sortByScore();
            textQuery.limit(50);
            textQuery.addCriteria(Criteria.where("active").is(true));
            for (VocabWord w : mongoTemplate.find(textQuery, VocabWord.class)) {
                merged.putIfAbsent(w.getId(), w);
            }
        }

        List<VocabWord> result = merged.values().stream().limit(50).toList();
        if (result.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> lessonNoById = lessonRepository.findAll().stream()
                .collect(Collectors.toMap(Lesson::getId, Lesson::getLessonNo, (a, b) -> a));
        return result.stream()
                .map(w -> WordMapper.toSearchDto(w, lessonNoById.getOrDefault(w.getLessonId(), 0)))
                .toList();
    }
}
