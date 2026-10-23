package com.masterjp.service;

import com.masterjp.domain.ScopeType;
import com.masterjp.domain.SessionMode;
import com.masterjp.domain.StudySession;
import com.masterjp.domain.User;
import com.masterjp.dto.SessionDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.StudySessionRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.util.AppTime;
import com.masterjp.util.EnumUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Ghi phien hoc (F-020—F-023, F-026): backend tu tinh totalItems/correctCount
 * tu items va cap nhat streak (F-029) — client khong tu tinh streak.
 */
@Service
public class SessionService {

    private final StudySessionRepository studySessionRepository;
    private final UserRepository userRepository;
    private final AppTime appTime;
    private final BadgeService badgeService;
    private final WrongAnswerService wrongAnswerService;

    public SessionService(StudySessionRepository studySessionRepository,
                          UserRepository userRepository, AppTime appTime,
                          BadgeService badgeService, WrongAnswerService wrongAnswerService) {
        this.studySessionRepository = studySessionRepository;
        this.userRepository = userRepository;
        this.appTime = appTime;
        this.badgeService = badgeService;
        this.wrongAnswerService = wrongAnswerService;
    }

    public SessionDtos.SessionResponse create(String userId, SessionDtos.CreateSessionRequest req) {
        SessionMode mode = EnumUtils.parse(SessionMode.class, req.mode(), "mode");
        ScopeType scopeType = EnumUtils.parse(ScopeType.class, req.scopeType(), "scopeType");

        List<StudySession.SessionItem> items = req.items() == null ? List.of()
                : req.items().stream()
                .filter(i -> i != null && i.wordId() != null && i.correct() != null)
                .map(i -> new StudySession.SessionItem(i.wordId(), i.correct(), i.timeMs()))
                .toList();

        StudySession session = new StudySession();
        session.setUserId(userId);
        session.setMode(mode);
        session.setScopeType(scopeType);
        session.setScopeId(req.scopeId());
        session.setTotalItems(items.size());
        session.setCorrectCount((int) items.stream().filter(StudySession.SessionItem::isCorrect).count());
        session.setItems(items);
        session.setStartedAt(req.startedAt() != null ? req.startedAt() : new Date());
        session.setFinishedAt(req.finishedAt() != null ? req.finishedAt() : new Date());
        studySessionRepository.save(session);

        // Phase C — F-043: ghi cau sai vao wrong_answers (dedupe + retention nam trong service).
        wrongAnswerService.recordFromSession(userId, session.getId(), session.getItems());

        int streakDays = updateStreak(userId);

        // Phase C — F-042: trao huy hieu SAU khi streak da cap nhat (badge streak
        // doc streakDays moi nhat tu DB — hook chay truoc updateStreak se trao tre 1 phien).
        List<String> newBadges = badgeService.onSessionCompleted(session);

        return new SessionDtos.SessionResponse(session.getId(), streakDays, newBadges);
    }

    /**
     * Streak: moi POST /api/sessions thanh cong -> so lastStudyDate voi hom nay.
     * Khac ngay: lien tiep (hom qua) -> +1, khong thi reset ve 1.
     */
    private int updateStreak(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                        "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND));
        LocalDate today = appTime.today();
        LocalDate last = user.getLastStudyDate() == null
                ? null : appTime.toLocalDate(user.getLastStudyDate());
        if (last == null || !last.equals(today)) {
            if (last != null && last.equals(today.minusDays(1))) {
                user.setStreakDays(user.getStreakDays() + 1);
            } else {
                user.setStreakDays(1);
            }
            user.setLastStudyDate(appTime.toDate(today));
            userRepository.save(user);
        }
        return user.getStreakDays();
    }
}
