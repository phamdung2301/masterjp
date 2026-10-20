package com.masterjp.service;

import com.masterjp.domain.Grammar;
import com.masterjp.dto.GrammarDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.GrammarRepository;
import com.masterjp.util.VietnameseUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/** Feature: Ngu phap — noi dung public read (GUEST khong can auth). V1 read-only. */
@Service
public class GrammarService {

    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.asc("lessonNo"),
            Sort.Order.asc("orderInLesson"));

    private final GrammarRepository grammarRepository;
    private final MongoTemplate mongoTemplate;

    public GrammarService(GrammarRepository grammarRepository, MongoTemplate mongoTemplate) {
        this.grammarRepository = grammarRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * GET /api/grammar?lessonNo=&level= — tat ca param optional.
     * level chi chap nhan "N5"/"N4" sau khi normalize (trim + uppercase);
     * gia tri la -> bo qua filter (theo mau topic la trong KanjiService.listKanji).
     * Luon loc active=true; sap theo lessonNo ASC roi orderInLesson ASC.
     */
    public List<GrammarDtos.GrammarDto> list(Integer lessonNo, String level) {
        String levelNorm = null;
        if (level != null && !level.isBlank()) {
            String n = level.trim().toUpperCase();
            if ("N5".equals(n) || "N4".equals(n)) {
                levelNorm = n;
            }
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("active").is(true));
        if (lessonNo != null) {
            query.addCriteria(Criteria.where("lessonNo").is(lessonNo));
        }
        if (levelNorm != null) {
            query.addCriteria(Criteria.where("level").is(levelNorm));
        }
        query.with(LIST_SORT);
        return mongoTemplate.find(query, Grammar.class).stream().map(this::toListDto).toList();
    }

    /**
     * GET /api/grammar/{id} — chi tiet. Khong thay hoac da inactive ->
     * 404 GRAMMAR_NOT_FOUND (theo mau KanjiService.getDetail).
     */
    public GrammarDtos.GrammarDetailDto detail(String id) {
        Grammar g = grammarRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException("GRAMMAR_NOT_FOUND",
                        "Không tìm thấy điểm ngữ pháp này.", HttpStatus.NOT_FOUND));
        return toDetailDto(g);
    }

    /**
     * GET /api/grammar/search?q= — q rong -> list rong.
     * Khop khi: searchNorm chua chuoi da normalize bang VietnameseUtils
     * (tieng Viet go khong dau van ra) HOAC title chua q goc khong phan biet
     * hoa thuong (tim theo mau Nhat, vd. "てください"). Luon loc active=true,
     * sap nhu list, gioi han 50 ket qua.
     * searchNorm da lowercase tu luc seed nen regex tren searchNorm khong can "i";
     * (title goc chua dau Nhat/latin ne regex tren title dung "i" cho an toan.)
     */
    public List<GrammarDtos.GrammarDto> search(String q) {
        String raw = q == null ? "" : q.trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        String normalized = VietnameseUtils.normalize(raw);
        Criteria orCriteria;
        if (normalized.isEmpty()) {
            // q chi gom ky tu mat dau khi chuan hoa -> chi tim theo mau Nhat (title).
            orCriteria = Criteria.where("title").regex(Pattern.quote(raw), "i");
        } else {
            orCriteria = new Criteria().orOperator(
                    Criteria.where("searchNorm").regex(Pattern.quote(normalized)),
                    Criteria.where("title").regex(Pattern.quote(raw), "i"));
        }
        Criteria searchCriteria = new Criteria().andOperator(
                Criteria.where("active").is(true),
                orCriteria);
        Query query = new Query(searchCriteria);
        query.with(LIST_SORT);
        query.limit(50);
        return mongoTemplate.find(query, Grammar.class).stream().map(this::toListDto).toList();
    }

    private GrammarDtos.GrammarDto toListDto(Grammar g) {
        return new GrammarDtos.GrammarDto(
                g.getId(), g.getLessonNo(), g.getLevel(),
                g.getTitle(), g.getTitleVi(), g.getPattern(),
                g.getMeaningVi(), g.getTags());
    }

    private GrammarDtos.GrammarDetailDto toDetailDto(Grammar g) {
        List<GrammarDtos.ExampleDto> examples = g.getExamples() == null ? List.of()
                : g.getExamples().stream()
                        .map(e -> new GrammarDtos.ExampleDto(e.getJa(), e.getKana(), e.getVi()))
                        .toList();
        return new GrammarDtos.GrammarDetailDto(
                g.getId(), g.getLessonNo(), g.getLevel(),
                g.getTitle(), g.getTitleVi(), g.getPattern(),
                g.getMeaningVi(), g.getTags(),
                g.getExplanation(), g.getFormation(),
                examples, g.getNotes(), g.getCompareWith());
    }
}
