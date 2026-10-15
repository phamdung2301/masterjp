package com.masterjp.service;

import com.masterjp.domain.CustomSet;
import com.masterjp.domain.Plan;
import com.masterjp.domain.Role;
import com.masterjp.domain.User;
import com.masterjp.domain.VocabWord;
import com.masterjp.dto.SetDtos;
import com.masterjp.dto.WordDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.CustomSetRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.repository.VocabWordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bo the tu chon (F-024/F-025/F-035).
 * Quyen chia se cong khai do backend enforce theo SHARING_ALLOWED_PLANS (ENV,
 * mac dinh PREMIUM,ADMIN) — FE chi an nut.
 */
@Service
public class SetService {

    private final CustomSetRepository customSetRepository;
    private final VocabWordRepository vocabWordRepository;
    private final UserRepository userRepository;
    private final Set<String> sharingAllowed;

    public SetService(CustomSetRepository customSetRepository,
                      VocabWordRepository vocabWordRepository,
                      UserRepository userRepository,
                      @Value("${app.sharing-allowed-plans:PREMIUM,ADMIN}") String sharingAllowedPlans) {
        this.customSetRepository = customSetRepository;
        this.vocabWordRepository = vocabWordRepository;
        this.userRepository = userRepository;
        this.sharingAllowed = Arrays.stream(sharingAllowedPlans.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    // ---------- CRUD ----------

    public List<SetDtos.SetDto> listMine(String userId) {
        return customSetRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toDto).toList();
    }

    public SetDtos.SetDto create(String userId, SetDtos.CreateSetRequest req) {
        if (customSetRepository.countByUserId(userId) >= CustomSet.MAX_SETS_PER_USER) {
            throw new ApiException("SET_LIMIT_REACHED",
                    "Bạn đã tạo tối đa " + CustomSet.MAX_SETS_PER_USER + " bộ thẻ.",
                    HttpStatus.BAD_REQUEST);
        }
        CustomSet set = new CustomSet();
        set.setUserId(userId);
        set.setName(req.name().trim());
        set.setDescription(req.description() == null ? null : req.description().trim());
        customSetRepository.save(set);
        return toDto(set);
    }

    public SetDtos.SetDto update(String userId, String id, SetDtos.UpdateSetRequest req) {
        CustomSet set = ownedSet(userId, id);
        if (req.name() != null && !req.name().isBlank()) {
            set.setName(req.name().trim());
        }
        if (req.description() != null) {
            set.setDescription(req.description().trim());
        }
        set.setUpdatedAt(new Date());
        customSetRepository.save(set);
        return toDto(set);
    }

    public void delete(String userId, String id, boolean isAdmin) {
        CustomSet set = customSetRepository.findById(id)
                .orElseThrow(() -> new ApiException("SET_NOT_FOUND",
                        "Bộ thẻ không tồn tại.", HttpStatus.NOT_FOUND));
        if (!isAdmin && !set.getUserId().equals(userId)) {
            throw new ApiException("FORBIDDEN",
                    "Bạn không có quyền xóa bộ thẻ này.", HttpStatus.FORBIDDEN);
        }
        customSetRepository.delete(set);
    }

    /** GET /api/sets/{id} — owner hoac public (kem words). */
    public SetDtos.SetDetailDto get(String userId, String id) {
        CustomSet set = customSetRepository.findById(id)
                .orElseThrow(() -> new ApiException("SET_NOT_FOUND",
                        "Bộ thẻ không tồn tại.", HttpStatus.NOT_FOUND));
        if (!set.isPublic() && (userId == null || !set.getUserId().equals(userId))) {
            throw new ApiException("SET_NOT_FOUND",
                    "Bộ thẻ không tồn tại.", HttpStatus.NOT_FOUND);
        }
        List<WordDtos.WordDto> words = List.of();
        if (!set.getWordIds().isEmpty()) {
            Map<String, VocabWord> wordById = vocabWordRepository
                    .findByIdInAndActiveTrue(set.getWordIds()).stream()
                    .collect(Collectors.toMap(VocabWord::getId, w -> w, (a, b) -> a));
            words = set.getWordIds().stream()
                    .map(wordById::get)
                    .filter(w -> w != null)
                    .map(w -> WordMapper.toDto(w, null))
                    .toList();
        }
        return new SetDtos.SetDetailDto(set.getId(), set.getName(), set.getDescription(),
                set.getWordIds().size(), set.isPublic(),
                set.getCreatedAt(), set.getUpdatedAt(), words);
    }

    // ---------- Them / xoa tu (F-025) ----------

    public SetDtos.AddWordsResponse addWords(String userId, String id, List<String> wordIds) {
        CustomSet set = ownedSet(userId, id);
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(wordIds));

        List<VocabWord> found = vocabWordRepository.findByIdInAndActiveTrue(distinct);
        if (found.size() != distinct.size()) {
            Set<String> foundIds = found.stream().map(VocabWord::getId).collect(Collectors.toSet());
            String missing = distinct.stream().filter(x -> !foundIds.contains(x))
                    .findFirst().orElse("?");
            throw new ApiException("WORD_NOT_FOUND",
                    "Có từ không tồn tại (id: " + missing + ").", HttpStatus.BAD_REQUEST);
        }

        int added = 0;
        int skipped = 0;
        for (String wordId : distinct) {
            if (set.getWordIds().contains(wordId)) {
                skipped++;
            } else {
                set.getWordIds().add(wordId);
                added++;
            }
        }
        if (set.getWordIds().size() > CustomSet.MAX_WORDS) {
            throw new ApiException("SET_WORD_LIMIT",
                    "Bộ thẻ đã đạt tối đa " + CustomSet.MAX_WORDS + " từ.",
                    HttpStatus.BAD_REQUEST);
        }
        set.setUpdatedAt(new Date());
        customSetRepository.save(set);
        return new SetDtos.AddWordsResponse(added, skipped);
    }

    public void removeWord(String userId, String id, String wordId) {
        CustomSet set = ownedSet(userId, id);
        set.getWordIds().removeIf(w -> w.equals(wordId));
        set.setUpdatedAt(new Date());
        customSetRepository.save(set);
    }

    // ---------- Chia se cong khai (F-035) ----------

    public SetDtos.SetDto share(String userId, String id, boolean isPublic) {
        CustomSet set = ownedSet(userId, id);
        if (isPublic) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                            "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND));
            if (!canSharePublic(user)) {
                throw new ApiException("SHARING_FORBIDDEN",
                        "Nâng cấp gói để chia sẻ công khai", HttpStatus.FORBIDDEN);
            }
        }
        set.setPublic(isPublic);
        set.setUpdatedAt(new Date());
        customSetRepository.save(set);
        return toDto(set);
    }

    /** GET /api/sets/public — KHONG can auth. */
    public SetDtos.PublicSetsResponse publicSets(String q, int page) {
        String query = q == null ? "" : q.trim();
        Page<CustomSet> result = customSetRepository.findByIsPublicTrueAndNameContainingIgnoreCase(
                query, PageRequest.of(Math.max(page, 0), 20, Sort.by(Sort.Direction.DESC, "updatedAt")));
        Map<String, String> authorById = userRepository.findAllById(
                        result.getContent().stream().map(CustomSet::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        List<SetDtos.PublicSetDto> items = result.getContent().stream()
                .map(s -> new SetDtos.PublicSetDto(s.getId(), s.getName(), s.getDescription(),
                        s.getWordIds().size(),
                        authorById.getOrDefault(s.getUserId(), "Người dùng"),
                        s.getForkCount(), s.getUpdatedAt()))
                .toList();
        return new SetDtos.PublicSetsResponse(items, result.getTotalElements());
    }

    /** POST /api/sets/{id}/fork — sao chep thanh bo rieng tu cua minh. */
    public SetDtos.ForkResponse fork(String userId, String id) {
        // "Bản sao" cũng là 1 bộ thẻ nên tính vào giới hạn 50 bộ/user (F-024)
        if (customSetRepository.countByUserId(userId) >= CustomSet.MAX_SETS_PER_USER) {
            throw new ApiException("SET_LIMIT_REACHED",
                    "Bạn đã tạo tối đa " + CustomSet.MAX_SETS_PER_USER + " bộ thẻ.",
                    HttpStatus.BAD_REQUEST);
        }
        CustomSet origin = customSetRepository.findById(id)
                .orElseThrow(() -> new ApiException("SET_NOT_FOUND",
                        "Bộ thẻ không tồn tại.", HttpStatus.NOT_FOUND));
        if (!origin.isPublic() && !origin.getUserId().equals(userId)) {
            throw new ApiException("SET_NOT_FOUND",
                    "Bộ thẻ không tồn tại.", HttpStatus.NOT_FOUND);
        }
        CustomSet copy = new CustomSet();
        copy.setUserId(userId);
        copy.setName(origin.getName() + " (bản sao)");
        copy.setDescription(origin.getDescription());
        copy.setWordIds(new ArrayList<>(origin.getWordIds()));
        copy.setPublic(false);
        customSetRepository.save(copy);

        origin.setForkCount(origin.getForkCount() + 1);
        customSetRepository.save(origin);

        return new SetDtos.ForkResponse(copy.getId(), copy.getName());
    }

    // ---------- helpers ----------

    private CustomSet ownedSet(String userId, String id) {
        return customSetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException("SET_NOT_FOUND",
                        "Bộ thẻ không tồn tại.", HttpStatus.NOT_FOUND));
    }

    private boolean canSharePublic(User user) {
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        Plan plan = user.getPlan() == null ? Plan.FREE : user.getPlan();
        return sharingAllowed.contains(plan.name());
    }

    private SetDtos.SetDto toDto(CustomSet set) {
        return new SetDtos.SetDto(set.getId(), set.getName(), set.getDescription(),
                set.getWordIds().size(), set.isPublic(),
                set.getCreatedAt(), set.getUpdatedAt());
    }
}
