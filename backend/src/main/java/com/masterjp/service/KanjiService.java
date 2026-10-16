package com.masterjp.service;

import com.masterjp.domain.Kanji;
import com.masterjp.domain.Lesson;
import com.masterjp.domain.VocabWord;
import com.masterjp.dto.KanjiDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.KanjiRepository;
import com.masterjp.repository.LessonRepository;
import com.masterjp.repository.VocabWordRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** F-B (Feature Pack 2): noi dung kanji public read (GUEST khong can auth). */
@Service
public class KanjiService {

    /**
     * Taxonomy chu de kanji (17 nhom) — single source of truth cho label.
     * Data phan loai: data/seed/kanji-topics.json (mapping character -> code).
     * Thu tu = so luong giam dan (theo du lieu seed).
     */
    public static final LinkedHashMap<String, String> TOPIC_LABELS = new LinkedHashMap<>();

    static {
        TOPIC_LABELS.put("sinh_hoat", "Sinh hoạt");
        TOPIC_LABELS.put("truu_tuong", "Trừu tượng");
        TOPIC_LABELS.put("tinh_chat", "Tính chất");
        TOPIC_LABELS.put("con_nguoi_co_the", "Con người & cơ thể");
        TOPIC_LABELS.put("gia_dinh_xa_hoi", "Gia đình & xã hội");
        TOPIC_LABELS.put("noi_chon_huong", "Nơi chốn & phương hướng");
        TOPIC_LABELS.put("giao_tiep", "Giao tiếp");
        TOPIC_LABELS.put("hoc_tap_cong_viec", "Học tập & công việc");
        TOPIC_LABELS.put("thoi_gian", "Thời gian");
        TOPIC_LABELS.put("thien_nhien", "Thiên nhiên");
        TOPIC_LABELS.put("an_uong", "Ăn uống");
        TOPIC_LABELS.put("kinh_te", "Kinh tế");
        TOPIC_LABELS.put("so_dem", "Số đếm");
        TOPIC_LABELS.put("dong_thuc_vat", "Động & thực vật");
        TOPIC_LABELS.put("cam_xuc", "Cảm xúc");
        TOPIC_LABELS.put("mau_sac", "Màu sắc");
        TOPIC_LABELS.put("khac", "Khác");
    }

    private final KanjiRepository kanjiRepository;
    private final VocabWordRepository vocabWordRepository;
    private final LessonRepository lessonRepository;
    private final MongoTemplate mongoTemplate;

    public KanjiService(KanjiRepository kanjiRepository,
                        VocabWordRepository vocabWordRepository,
                        LessonRepository lessonRepository,
                        MongoTemplate mongoTemplate) {
        this.kanjiRepository = kanjiRepository;
        this.vocabWordRepository = vocabWordRepository;
        this.lessonRepository = lessonRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * GET /api/kanji?lessonNo=&jlpt=&topic= — tat ca param optional;
     * topic nhan nhieu code cach nhau dau phay. Khong param nao -> tat ca chu,
     * luon sap theo so net tang dan.
     */
    public List<KanjiDtos.KanjiDto> listKanji(Integer lessonNo, String jlpt, List<String> topics) {
        String jlptNorm = jlpt == null || jlpt.isBlank() ? null : jlpt.trim().toUpperCase();
        List<String> topicNorm = (topics == null) ? List.of()
                : topics.stream()
                        .filter(t -> t != null && !t.isBlank())
                        .map(t -> t.trim().toLowerCase())
                        .filter(TOPIC_LABELS::containsKey)
                        .distinct()
                        .toList();
        Query query = new Query();
        if (lessonNo != null) {
            query.addCriteria(Criteria.where("lessonNos").in(lessonNo));
        }
        if (jlptNorm != null) {
            query.addCriteria(Criteria.where("jlpt").is(jlptNorm));
        }
        if (!topicNorm.isEmpty()) {
            query.addCriteria(Criteria.where("topic").in(new ArrayList<>(topicNorm)));
        }
        query.with(Sort.by(Sort.Direction.ASC, "strokes"));
        return mongoTemplate.find(query, Kanji.class).stream().map(this::toDto).toList();
    }

    /**
     * GET /api/kanji/topics — 17 chu de + so chu thuc te trong DB.
     * Count = 0 van tra ve (de FE hien du, khong an chu de).
     */
    public List<KanjiDtos.KanjiTopicDto> listTopics() {
        List<KanjiDtos.KanjiTopicDto> out = new ArrayList<>();
        for (Map.Entry<String, String> e : TOPIC_LABELS.entrySet()) {
            out.add(new KanjiDtos.KanjiTopicDto(e.getKey(), e.getValue(),
                    kanjiRepository.countByTopic(e.getKey())));
        }
        return out;
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
                kanji.getLessonNos(), kanji.getTopic(),
                words.stream().map(a -> WordMapper.toDto(a.word(), null)).toList());
    }

    private KanjiDtos.KanjiDto toDto(Kanji k) {
        return new KanjiDtos.KanjiDto(k.getCharacter(), k.getOnYomi(), k.getKunYomi(),
                k.getMeaningVi(), k.getStrokes(), k.getJlpt(), k.getLessonNos(), k.getTopic());
    }

    /** Record noi bo de sap xep tu theo lessonNo truoc khi map sang DTO. */
    private record WordWithLessonNo(VocabWord word, int lessonNo) {
    }
}
