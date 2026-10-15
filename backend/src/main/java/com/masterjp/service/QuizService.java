package com.masterjp.service;

import com.masterjp.domain.CustomSet;
import com.masterjp.domain.Lesson;
import com.masterjp.domain.Level;
import com.masterjp.domain.QuizMode;
import com.masterjp.domain.ScopeType;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordProgress;
import com.masterjp.domain.WordSection;
import com.masterjp.dto.QuizDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.CustomSetRepository;
import com.masterjp.repository.LessonRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WordProgressRepository;
import com.masterjp.util.EnumUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sinh bo cau hoi quiz (F-021/F-022/F-023): moi cau 1 dap an dung + 3 dap an
 * nhieu (cung level, uu tien cung loai tu, khong trung nhau).
 * Mode TYPING (F-037): khong can dap an nhieu — tra kana/kanji/meaningVi de
 * FE cham local (wanakana), chi can >= 1 tu.
 * NFR-001: tai ca bo cau hoi 1 lan — khong cho server giua cac cau.
 */
@Service
public class QuizService {

    private final VocabWordRepository vocabWordRepository;
    private final LessonRepository lessonRepository;
    private final CustomSetRepository customSetRepository;
    private final WordProgressRepository wordProgressRepository;

    public QuizService(VocabWordRepository vocabWordRepository,
                       LessonRepository lessonRepository,
                       CustomSetRepository customSetRepository,
                       WordProgressRepository wordProgressRepository) {
        this.vocabWordRepository = vocabWordRepository;
        this.lessonRepository = lessonRepository;
        this.customSetRepository = customSetRepository;
        this.wordProgressRepository = wordProgressRepository;
    }

    public QuizDtos.QuizResponse generate(String userId, QuizDtos.QuizGenerateRequest req) {
        QuizMode mode = EnumUtils.parse(QuizMode.class, req.mode(), "mode");
        ScopeType scopeType = EnumUtils.parse(ScopeType.class, req.scopeType(), "scopeType");

        List<VocabWord> scopeWords = resolveScope(scopeType, req.scopeId(), userId);
        return buildQuestions(scopeWords, mode, req.limit());
    }

    /**
     * F-C (Feature Pack 2): luyen nhanh tu danh sach wordIds chon tay.
     * Validate: 1–100 tu; LISTENING can >= 4 tu (dap an nhieu) — thieu thi
     * QUIZ_NOT_ENOUGH_WORDS. ids khong ton tai / inactive -> bo qua.
     * KHONG tao CustomSet. KHONG tao StudySession o day — FE ghi lich su sau
     * khi lam bai xong qua POST /api/sessions (scopeType=QUICK, scopeId="quick")
     * dung luong quiz thuong; SM-2 cap nhat qua PATCH /api/progress/answer.
     * Response dung shape QuizResponse (FE tai dung component Quiz).
     */
    public QuizDtos.QuizResponse generateQuick(String userId, QuizDtos.QuickQuizRequest req) {
        QuizMode mode = EnumUtils.parse(QuizMode.class, req.mode(), "mode");
        List<String> ids = req.wordIds() == null ? List.of()
                : req.wordIds().stream().filter(Objects::nonNull).distinct().toList();
        List<VocabWord> words = ids.isEmpty() ? List.of()
                : vocabWordRepository.findByIdInAndActiveTrue(ids);
        if (words.isEmpty()) {
            throw new ApiException("QUIZ_NOT_ENOUGH_WORDS",
                    "Không tìm thấy từ nào hợp lệ trong danh sách đã chọn.",
                    HttpStatus.BAD_REQUEST);
        }
        return buildQuestions(words, mode, words.size());
    }

    /**
     * Logic sinh cau hoi chung tu danh sach tu -> QuizResponse.
     * Dung chung cho POST /api/quiz/generate (scope-based) va
     * POST /api/quiz/quick (danh sach tu chon tay) — F-C.
     */
    private QuizDtos.QuizResponse buildQuestions(List<VocabWord> words, QuizMode mode,
                                                  Integer limitReq) {
        List<VocabWord> candidates = new ArrayList<>(words);
        if (mode == QuizMode.LISTENING) {
            candidates = candidates.stream()
                    .filter(w -> w.getAudioUrl() != null && !w.getAudioUrl().isBlank())
                    .toList();
        }
        if (mode == QuizMode.TYPING) {
            return generateTyping(candidates, limitReq);
        }
        if (candidates.size() < 4) {
            throw new ApiException("QUIZ_NOT_ENOUGH_WORDS",
                    "Không đủ từ để tạo quiz (cần ít nhất 4 từ). Hãy mở rộng phạm vi học.",
                    HttpStatus.BAD_REQUEST);
        }

        int limit = (limitReq == null || limitReq <= 0) ? 20
                : Math.min(limitReq, candidates.size());
        List<VocabWord> picked = new ArrayList<>(candidates);
        Collections.shuffle(picked);
        picked = picked.subList(0, limit);

        // Pool dap an nhieu theo level
        Map<String, Level> levelByLesson = lessonRepository.findAll().stream()
                .collect(Collectors.toMap(Lesson::getId, Lesson::getLevel, (a, b) -> a));
        Map<Level, List<VocabWord>> poolByLevel = vocabWordRepository.findByActiveTrue().stream()
                .filter(w -> levelByLesson.containsKey(w.getLessonId()))
                .collect(Collectors.groupingBy(w -> levelByLesson.get(w.getLessonId())));

        Set<String> pickedIds = picked.stream().map(VocabWord::getId).collect(Collectors.toSet());

        List<QuizDtos.QuizQuestionDto> questions = new ArrayList<>();
        for (VocabWord word : picked) {
            Level level = levelByLesson.get(word.getLessonId());
            List<VocabWord> pool = new ArrayList<>(poolByLevel.getOrDefault(level, List.of()));
            pool.removeIf(w -> w.getId().equals(word.getId()) || pickedIds.contains(w.getId()));

            List<VocabWord> distractors = pickDistractors(pool, word);
            if (distractors.size() < 3) {
                throw new ApiException("QUIZ_NOT_ENOUGH_WORDS",
                        "Không đủ từ cùng cấp độ để tạo đáp án nhiễu.",
                        HttpStatus.BAD_REQUEST);
            }

            List<QuizDtos.QuizOptionDto> options = new ArrayList<>();
            options.add(new QuizDtos.QuizOptionDto(word.getId(), optionText(word, mode)));
            for (VocabWord d : distractors) {
                options.add(new QuizDtos.QuizOptionDto(d.getId(), optionText(d, mode)));
            }
            Collections.shuffle(options);

            questions.add(new QuizDtos.QuizQuestionDto(
                    word.getId(),
                    promptText(word, mode),
                    word.getAudioUrl(),
                    options,
                    word.getId(),
                    null, null, null));
        }
        return new QuizDtos.QuizResponse(questions);
    }

    /**
     * F-037: typing practice — khong dap an nhieu; FE cham local bang kana chuan.
     */
    private QuizDtos.QuizResponse generateTyping(List<VocabWord> scopeWords, Integer limitReq) {
        if (scopeWords.isEmpty()) {
            throw new ApiException("QUIZ_NOT_ENOUGH_WORDS",
                    "Không đủ từ để luyện gõ phiên âm (cần ít nhất 1 từ). Hãy mở rộng phạm vi học.",
                    HttpStatus.BAD_REQUEST);
        }
        int limit = (limitReq == null || limitReq <= 0) ? 20
                : Math.min(limitReq, scopeWords.size());
        List<VocabWord> picked = new ArrayList<>(scopeWords);
        Collections.shuffle(picked);
        picked = picked.subList(0, limit);

        List<QuizDtos.QuizQuestionDto> questions = new ArrayList<>();
        for (VocabWord word : picked) {
            questions.add(new QuizDtos.QuizQuestionDto(
                    word.getId(),
                    word.displayForm(),
                    word.getAudioUrl(),
                    List.of(),
                    word.getId(),
                    word.getKana(),
                    word.getKanji(),
                    word.getMeaningVi()));
        }
        return new QuizDtos.QuizResponse(questions);
    }

    private List<VocabWord> pickDistractors(List<VocabWord> pool, VocabWord word) {
        List<VocabWord> result = new ArrayList<>();
        List<VocabWord> samePos = new ArrayList<>(pool.stream()
                .filter(w -> Objects.equals(w.getPos(), word.getPos()))
                .toList());
        Collections.shuffle(samePos);
        result.addAll(samePos.subList(0, Math.min(3, samePos.size())));
        if (result.size() < 3) {
            List<VocabWord> rest = new ArrayList<>(pool);
            rest.removeIf(result::contains);
            Collections.shuffle(rest);
            result.addAll(rest.subList(0, Math.min(3 - result.size(), rest.size())));
        }
        return result;
    }

    private String promptText(VocabWord word, QuizMode mode) {
        return switch (mode) {
            case JP_VI -> word.displayForm();
            case VI_JP -> word.getMeaningVi();
            case LISTENING -> "Nghe và chọn từ đúng";
            case TYPING -> word.displayForm(); // unreachable: TYPING tra ve som o generate()
        };
    }

    private String optionText(VocabWord word, QuizMode mode) {
        return switch (mode) {
            case JP_VI -> word.getMeaningVi();
            case VI_JP, LISTENING -> word.displayForm();
            case TYPING -> word.getMeaningVi(); // unreachable: TYPING tra ve som o generate()
        };
    }

    private List<VocabWord> resolveScope(ScopeType scopeType, String scopeId, String userId) {
        return switch (scopeType) {
            case LESSON -> {
                Lesson lesson = lessonRepository.findById(scopeId)
                        .orElseThrow(() -> new ApiException("LESSON_NOT_FOUND",
                                "Bài học không tồn tại.", HttpStatus.NOT_FOUND));
                yield vocabWordRepository.findByLessonIdAndActiveTrueOrderByOrderInLessonAsc(lesson.getId());
            }
            case SECTION -> {
                // scopeId = "lessonId:SECTION"
                String[] parts = scopeId.split(":", 2);
                if (parts.length != 2) {
                    throw new ApiException("VALIDATION_ERROR",
                            "scopeId của SECTION phải có dạng \"lessonId:SECTION\".",
                            HttpStatus.BAD_REQUEST);
                }
                Lesson lesson = lessonRepository.findById(parts[0])
                        .orElseThrow(() -> new ApiException("LESSON_NOT_FOUND",
                                "Bài học không tồn tại.", HttpStatus.NOT_FOUND));
                WordSection section = EnumUtils.parse(WordSection.class, parts[1], "section");
                yield vocabWordRepository.findByLessonIdAndSectionAndActiveTrueOrderByOrderInLessonAsc(
                        lesson.getId(), section);
            }
            case SET -> {
                CustomSet set = customSetRepository.findById(scopeId)
                        .orElseThrow(() -> new ApiException("SET_NOT_FOUND",
                                "Bộ thẻ không tồn tại.", HttpStatus.NOT_FOUND));
                if (!set.getUserId().equals(userId) && !set.isPublic()) {
                    throw new ApiException("FORBIDDEN",
                            "Bạn không có quyền dùng bộ thẻ này.", HttpStatus.FORBIDDEN);
                }
                if (set.getWordIds().isEmpty()) {
                    yield List.of();
                }
                yield vocabWordRepository.findByIdInAndActiveTrue(set.getWordIds());
            }
            case REVIEW -> {
                // F-026: tu den han on cua user (gom tu nhieu bai, scopeId khong dung).
                // Qua han lau xep truoc — cung thu tu voi GET /api/progress/due.
                List<WordProgress> progresses = wordProgressRepository
                        .findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                                userId, new Date(), PageRequest.of(0, 100));
                if (progresses.isEmpty()) {
                    yield List.of();
                }
                yield vocabWordRepository.findByIdInAndActiveTrue(
                        progresses.stream().map(WordProgress::getWordId).toList());
            }
            case QUICK -> throw new ApiException("VALIDATION_ERROR",
                    "Luyện nhanh dùng POST /api/quiz/quick, không dùng scope này.",
                    HttpStatus.BAD_REQUEST);
        };
    }
}
