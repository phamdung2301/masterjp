package com.masterjp.service;

import com.masterjp.domain.StudySession;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WrongAnswer;
import com.masterjp.dto.ReviewDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WrongAnswerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * F-043: ghi va resolve cau tra loi sai (collection wrong_answers).
 * Ghi boi hook backend — KHONG co endpoint rieng:
 *  1. SessionService.create(): sau khi luu session thanh cong.
 *  2. AiService.gradeSentence(): khi score < 60.
 * (Xem snippet wiring trong bao cao ban giao — orchestrator chen vao 2 file do.)
 */
@Service
public class WrongAnswerService {

    private final WrongAnswerRepository wrongAnswerRepository;
    private final VocabWordRepository vocabWordRepository;

    public WrongAnswerService(WrongAnswerRepository wrongAnswerRepository,
                              VocabWordRepository vocabWordRepository) {
        this.wrongAnswerRepository = wrongAnswerRepository;
        this.vocabWordRepository = vocabWordRepository;
    }

    /**
     * Hook 1 — goi sau moi session luu thanh cong.
     * Voi moi item {correct:false} co wordId la id tu vung THAT (kiem tra DB —
     * bo qua "shadow:..." va cac wordId dac biet nhu ky tu kanji) -> insert
     * {kind:"vocab"}; dedupe: da co unresolved cho cung (userId, wordId) thi bo qua.
     */
    public void recordFromSession(String userId, String sessionId,
                                  List<StudySession.SessionItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        // M2 fix (security audit): thu thap wordId truoc, validate bang 1 query
        // duy nhat — tranh N+1 tren endpoint POST /api/sessions.
        List<String> wrongWordIds = items.stream()
                .filter(i -> i != null && !i.isCorrect())
                .map(StudySession.SessionItem::getWordId)
                .filter(w -> w != null && !w.isBlank())
                .distinct()
                .toList();
        if (wrongWordIds.isEmpty()) {
            return;
        }
        Set<String> validIds = vocabWordRepository.findByIdInAndActiveTrue(wrongWordIds).stream()
                .map(VocabWord::getId)
                .collect(Collectors.toSet());
        boolean inserted = false;
        for (String wordId : wrongWordIds) {
            // Chi ghi khi la id tu vung that (bo qua "shadow:..." cua F-041,
            // ky tu kanji cua review session, ...).
            if (!validIds.contains(wordId)) {
                continue;
            }
            if (wrongAnswerRepository.existsByUserIdAndKindAndWordIdAndResolvedFalse(
                    userId, WrongAnswer.KIND_VOCAB, wordId)) {
                continue; // dedupe: unresolved da ton tai
            }
            WrongAnswer wa = new WrongAnswer();
            wa.setUserId(userId);
            wa.setKind(WrongAnswer.KIND_VOCAB);
            wa.setWordId(wordId);
            wa.setSessionId(sessionId);
            wa.setCreatedAt(new Date());
            wrongAnswerRepository.save(wa);
            inserted = true;
        }
        if (inserted) {
            enforceUnresolvedCap(userId);
        }
    }

    /**
     * Hook 2 — goi tu AiService.gradeSentence() khi score < 60.
     * Khong dedupe (moi lan cham la 1 cau khac nhau).
     */
    public void recordFromAiGrade(String userId, String aiGradeId) {
        WrongAnswer wa = new WrongAnswer();
        wa.setUserId(userId);
        wa.setKind(WrongAnswer.KIND_AI_SENTENCE);
        wa.setAiGradeId(aiGradeId);
        wa.setCreatedAt(new Date());
        wrongAnswerRepository.save(wa);
        enforceUnresolvedCap(userId);
    }

    /**
     * PATCH /api/review/wrong/{id} — resolve cau sai.
     * Idempotent: da resolved thi tra 200 nhu thuong.
     * Id khong ton tai hoac khong thuoc user -> 404 NOT_FOUND.
     */
    public ReviewDtos.ResolveWrongResponse resolve(String userId, String id) {
        WrongAnswer wa = wrongAnswerRepository.findById(id)
                .filter(w -> userId.equals(w.getUserId()))
                .orElseThrow(() -> new ApiException("NOT_FOUND",
                        "Không tìm thấy mục ôn tập.", HttpStatus.NOT_FOUND));
        if (!wa.isResolved()) {
            wa.setResolved(true);
            wa.setResolvedAt(new Date());
            wrongAnswerRepository.save(wa);
        }
        return new ReviewDtos.ResolveWrongResponse(wa.getId(), true);
    }

    /**
     * Retention (spec 5.3): unresolved giu toi da 100 ban ghi/user —
     * vuot thi xoa cu nhat.
     */
    private void enforceUnresolvedCap(String userId) {
        long overflow = wrongAnswerRepository.countByUserIdAndResolvedFalse(userId)
                - WrongAnswer.MAX_UNRESOLVED_PER_USER;
        if (overflow <= 0) {
            return;
        }
        List<WrongAnswer> oldest = wrongAnswerRepository
                .findByUserIdAndResolvedFalseOrderByCreatedAtAsc(
                        userId, PageRequest.of(0, (int) Math.min(overflow, 1000)));
        wrongAnswerRepository.deleteAll(oldest);
    }
}
