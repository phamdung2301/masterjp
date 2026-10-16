package com.masterjp.service;

import com.masterjp.domain.Kanji;
import com.masterjp.domain.KanjiQuizMode;
import com.masterjp.dto.KanjiDtos;
import com.masterjp.dto.QuizDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.KanjiRepository;
import com.masterjp.util.EnumUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Track 3 (R1-R3 rebuild) — quiz kanji tu danh sach chu chon tay.
 *
 * POST /api/kanji/quiz sinh cau hoi tu {@code characters} (1..100):
 * - KANJI_MEANING: hien chu kanji -> chon nghia Viet (4 dap an).
 * - MEANING_KANJI: hien nghia Viet -> chon chu kanji (4 dap an).
 * - KANJI_TYPING: hien chu + nghia -> go cach doc; BE tra danh sach dap an
 *   dung la MOI on/kun trong truong `kana`, FE cham local (khong dap an nhieu).
 *
 * Dap an nhieu (4 lua chon): uu tien cac chu KHAC trong tap `characters`
 * do user chon -> fallback cac chu cung JLPT (query KanjiRepository) ->
 * loai dap an mo ho (text dap an trung voi dap an dung — nghia Viet trung
 * nhau — hoac nghia rong). Thu tu dap an duoc shuffle.
 *
 * V1 KHONG LUU SM-2 / PROGRESS KANJI: method nay chi sinh cau hoi, khong
 * tao StudySession, khong cap nhat WordProgress — kanji progress de phase 2.
 */
@Service
public class KanjiQuizService {

    private final KanjiRepository kanjiRepository;

    public KanjiQuizService(KanjiRepository kanjiRepository) {
        this.kanjiRepository = kanjiRepository;
    }

    public QuizDtos.QuizResponse generateQuiz(KanjiDtos.KanjiQuizRequest req) {
        KanjiQuizMode mode = EnumUtils.parse(KanjiQuizMode.class, req.mode(), "mode");

        // Chuẩn hoá: bỏ null/rỗng, dedupe; @Size(1..100) trên DTO chặn ở tầng
        // validate, đây là lớp phòng thủ thứ 2.
        List<String> characters = req.characters() == null ? List.of()
                : req.characters().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .distinct()
                        .toList();
        if (characters.isEmpty()) {
            throw new ApiException("VALIDATION_ERROR",
                    "Danh sách chữ kanji không được để trống.", HttpStatus.BAD_REQUEST);
        }

        // id của Kanji = character nên findAllById (CrudRepository) dùng được
        // trực tiếp; chữ nào không tồn tại trong DB thì bỏ qua.
        List<Kanji> targets = kanjiRepository.findAllById(characters);
        if (targets.isEmpty()) {
            throw new ApiException("QUIZ_NOT_ENOUGH_KANJI",
                    "Không tìm thấy chữ kanji nào hợp lệ trong danh sách đã chọn.",
                    HttpStatus.BAD_REQUEST);
        }

        if (mode == KanjiQuizMode.KANJI_TYPING) {
            return generateTyping(targets);
        }
        return generateMultipleChoice(targets, mode);
    }

    /**
     * Trắc nghiệm 4 đáp án — tái dùng shape QuizResponse của quiz từ vựng:
     * wordId/answerWordId = character; promptAudioUrl = null (kanji chưa có
     * audio); kana/kanji/meaningVi để null (chỉ typing mới dùng).
     */
    private QuizDtos.QuizResponse generateMultipleChoice(List<Kanji> targets, KanjiQuizMode mode) {
        // Mode trắc nghiệm cần nghĩa Việt (prompt hoặc option) — chữ nào chưa
        // có nghĩa thì bỏ qua thay vì vỡ cả lượt.
        List<Kanji> usable = targets.stream()
                .filter(k -> k.getMeaningVi() != null && !k.getMeaningVi().isBlank())
                .toList();
        if (usable.isEmpty()) {
            throw new ApiException("QUIZ_NOT_ENOUGH_KANJI",
                    "Các chữ đã chọn chưa có nghĩa tiếng Việt để tạo câu hỏi.",
                    HttpStatus.BAD_REQUEST);
        }

        List<Kanji> picked = new ArrayList<>(usable);
        Collections.shuffle(picked);
        Map<String, Kanji> byCharacter = usable.stream()
                .collect(Collectors.toMap(Kanji::getCharacter, k -> k, (a, b) -> a));

        List<QuizDtos.QuizQuestionDto> questions = new ArrayList<>();
        for (Kanji target : picked) {
            List<Kanji> distractors = pickDistractors(target, mode, byCharacter);
            if (distractors.size() < 3) {
                throw new ApiException("QUIZ_NOT_ENOUGH_KANJI",
                        "Không đủ chữ kanji cùng cấp độ để tạo đáp án nhiễu.",
                        HttpStatus.BAD_REQUEST);
            }

            List<QuizDtos.QuizOptionDto> options = new ArrayList<>();
            options.add(new QuizDtos.QuizOptionDto(target.getCharacter(), optionText(target, mode)));
            for (Kanji d : distractors) {
                options.add(new QuizDtos.QuizOptionDto(d.getCharacter(), optionText(d, mode)));
            }
            Collections.shuffle(options);

            questions.add(new QuizDtos.QuizQuestionDto(
                    target.getCharacter(),
                    promptText(target, mode),
                    null,
                    options,
                    target.getCharacter(),
                    null, null, null));
        }
        return new QuizDtos.QuizResponse(questions);
    }

    /**
     * KANJI_TYPING: hiện chữ + nghĩa → gõ cách đọc. Không đáp án nhiễu;
     * trả MỌI on/kun trong trường `kana` để FE chấm local.
     * V1: không lưu progress.
     */
    private QuizDtos.QuizResponse generateTyping(List<Kanji> targets) {
        List<Kanji> picked = new ArrayList<>(targets);
        Collections.shuffle(picked);

        List<QuizDtos.QuizQuestionDto> questions = new ArrayList<>();
        for (Kanji k : picked) {
            List<String> readings = new ArrayList<>();
            if (k.getOnYomi() != null) {
                readings.addAll(k.getOnYomi());
            }
            if (k.getKunYomi() != null) {
                readings.addAll(k.getKunYomi());
            }
            questions.add(new QuizDtos.QuizQuestionDto(
                    k.getCharacter(),
                    k.getCharacter() + " · " + nullSafe(k.getMeaningVi()),
                    null,
                    List.of(),
                    k.getCharacter(),
                    String.join(", ", readings), // mọi đáp án đúng: tất cả on/kun
                    k.getCharacter(),
                    k.getMeaningVi()));
        }
        return new QuizDtos.QuizResponse(questions);
    }

    /**
     * Đáp án nhiễu: (1) ưu tiên chữ KHÁC trong tập characters user chọn,
     * (2) fallback các chữ cùng JLPT trong DB; (3) loại đáp án mơ hồ —
     * text trùng đáp án đúng (nghĩa Việt trùng nhau), nghĩa rỗng, hoặc
     * trùng chữ đã chọn.
     */
    private List<Kanji> pickDistractors(Kanji target, KanjiQuizMode mode,
                                        Map<String, Kanji> byCharacter) {
        String correctText = optionText(target, mode);
        List<Kanji> result = new ArrayList<>();

        List<Kanji> inSet = new ArrayList<>(byCharacter.values());
        inSet.removeIf(k -> k.getCharacter().equals(target.getCharacter()));
        Collections.shuffle(inSet);
        for (Kanji k : inSet) {
            if (result.size() >= 3) {
                break;
            }
            if (isAmbiguous(k, target, mode, correctText, result)) {
                continue;
            }
            result.add(k);
        }

        if (result.size() < 3 && target.getJlpt() != null && !target.getJlpt().isBlank()) {
            List<Kanji> sameJlpt = kanjiRepository.findByJlptOrderByStrokesAsc(target.getJlpt());
            Collections.shuffle(sameJlpt);
            for (Kanji k : sameJlpt) {
                if (result.size() >= 3) {
                    break;
                }
                if (isAmbiguous(k, target, mode, correctText, result)) {
                    continue;
                }
                result.add(k);
            }
        }
        return result;
    }

    /** Đáp án mơ hồ: chính là chữ mục tiêu / text trùng đáp án đúng
     * (nghĩa Việt trùng nhau) / text rỗng / đã được chọn. */
    private boolean isAmbiguous(Kanji candidate, Kanji target, KanjiQuizMode mode,
                                String correctText, List<Kanji> picked) {
        if (candidate.getCharacter().equals(target.getCharacter())) {
            return true;
        }
        if (picked.stream().anyMatch(p -> p.getCharacter().equals(candidate.getCharacter()))) {
            return true;
        }
        String text = optionText(candidate, mode);
        return text == null || text.isBlank() || text.equals(correctText);
    }

    /**
     * LƯU Ý: switch PHẢI exhaustive trên mọi constant của KanjiQuizMode
     * (bài học: thêm constant mà switch thiếu case là vỡ compile).
     */
    private String promptText(Kanji k, KanjiQuizMode mode) {
        return switch (mode) {
            case KANJI_MEANING -> k.getCharacter();
            case MEANING_KANJI -> nullSafe(k.getMeaningVi());
            case KANJI_TYPING -> k.getCharacter(); // unreachable: TYPING trả về sớm
        };
    }

    private String optionText(Kanji k, KanjiQuizMode mode) {
        return switch (mode) {
            case KANJI_MEANING -> k.getMeaningVi();
            case MEANING_KANJI -> k.getCharacter();
            case KANJI_TYPING -> k.getMeaningVi(); // unreachable: TYPING trả về sớm
        };
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
