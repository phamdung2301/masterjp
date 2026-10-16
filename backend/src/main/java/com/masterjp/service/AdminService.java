package com.masterjp.service;

import com.masterjp.domain.AuditLog;
import com.masterjp.domain.Lesson;
import com.masterjp.domain.Level;
import com.masterjp.domain.Plan;
import com.masterjp.domain.Role;
import com.masterjp.domain.User;
import com.masterjp.domain.VocabWord;
import com.masterjp.domain.WordProgress;
import com.masterjp.domain.WordSection;
import com.masterjp.dto.AdminDtos;
import com.masterjp.dto.CommonDtos;
import com.masterjp.dto.LessonDtos;
import com.masterjp.dto.WordDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.AuditLogRepository;
import com.masterjp.repository.LessonRepository;
import com.masterjp.repository.StudySessionRepository;
import com.masterjp.repository.UserRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.repository.WordProgressRepository;
import com.masterjp.util.AppTime;
import com.masterjp.util.AudioFileUtils;
import com.masterjp.util.ClusterLabels;
import com.masterjp.util.EnumUtils;
import com.masterjp.util.VietnameseUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Nghiep vu admin (F-030—F-036, FR-034). Moi thao tac ghi audit_logs.
 */
@Service
public class AdminService {

    private static final int PAGE_SIZE = 20;

    private final LessonRepository lessonRepository;
    private final VocabWordRepository vocabWordRepository;
    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;
    private final WordProgressRepository wordProgressRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final AudioJobService audioJobService;
    private final MongoTemplate mongoTemplate;
    private final AppTime appTime;

    public AdminService(LessonRepository lessonRepository,
                        VocabWordRepository vocabWordRepository,
                        UserRepository userRepository,
                        StudySessionRepository studySessionRepository,
                        WordProgressRepository wordProgressRepository,
                        AuditLogRepository auditLogRepository,
                        AuditService auditService,
                        AudioJobService audioJobService,
                        MongoTemplate mongoTemplate,
                        AppTime appTime) {
        this.lessonRepository = lessonRepository;
        this.vocabWordRepository = vocabWordRepository;
        this.userRepository = userRepository;
        this.studySessionRepository = studySessionRepository;
        this.wordProgressRepository = wordProgressRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.audioJobService = audioJobService;
        this.mongoTemplate = mongoTemplate;
        this.appTime = appTime;
    }

    // ==================== Lessons (F-030) ====================

    /**
     * R1: strip prefix "Bài N — " nếu admin nhập thừa vào ô tiêu đề.
     * titleVi trong DB KHÔNG bao giờ chứa prefix (frontend tự render "Bài {lessonNo} — {titleVi}").
     */
    private static String stripLessonPrefix(String titleVi) {
        if (titleVi == null) {
            return null;
        }
        return titleVi.replaceFirst("^Bài \\d+ — ", "");
    }

    public List<LessonDtos.LessonDetailDto> listLessons() {
        List<Lesson> lessons = lessonRepository.findAll();
        lessons.sort((a, b) -> {
            int c = Integer.compare(a.getLevel().ordinal(), b.getLevel().ordinal());
            return c != 0 ? c : Integer.compare(a.getLessonNo(), b.getLessonNo());
        });
        return lessons.stream()
                .map(l -> new LessonDtos.LessonDetailDto(l.getId(), l.getLevel().name(),
                        l.getLessonNo(), l.getTitleJa(), l.getTitleVi(), l.getOrder()))
                .toList();
    }

    public LessonDtos.LessonDetailDto createLesson(String adminId, LessonDtos.CreateLessonRequest req) {
        Level level = EnumUtils.parse(Level.class, req.level(), "level");
        if (Level.forLessonNo(req.lessonNo()) != level) {
            throw new ApiException("VALIDATION_ERROR",
                    "lessonNo " + req.lessonNo() + " không thuộc cấp độ " + level + ".",
                    HttpStatus.BAD_REQUEST);
        }
        lessonRepository.findByLevelAndLessonNo(level, req.lessonNo()).ifPresent(l -> {
            throw new ApiException("LESSON_EXISTS",
                    "Đã có bài " + level + " số " + req.lessonNo() + ".", HttpStatus.CONFLICT);
        });
        Lesson lesson = new Lesson();
        lesson.setLevel(level);
        lesson.setLessonNo(req.lessonNo());
        lesson.setTitleJa(req.titleJa().trim());
        // R1: strip server-side — phòng admin nhập thừa "Bài 3 — ..." vào ô tiêu đề.
        lesson.setTitleVi(stripLessonPrefix(req.titleVi().trim()));
        lesson.setOrder(req.order());
        lessonRepository.save(lesson);
        auditService.log(adminId, "CREATE_LESSON", "lessons", lesson.getId(),
                Map.of("after", lesson.getLevel() + " #" + lesson.getLessonNo() + " " + lesson.getTitleVi()));
        return toDetail(lesson);
    }

    public LessonDtos.LessonDetailDto updateLesson(String adminId, String id, LessonDtos.UpdateLessonRequest req) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ApiException("LESSON_NOT_FOUND",
                        "Bài học không tồn tại.", HttpStatus.NOT_FOUND));
        String before = lesson.getLevel() + " #" + lesson.getLessonNo() + " " + lesson.getTitleVi();

        Level newLevel = req.level() == null ? lesson.getLevel()
                : EnumUtils.parse(Level.class, req.level(), "level");
        int newNo = req.lessonNo() == null ? lesson.getLessonNo() : req.lessonNo();
        if (Level.forLessonNo(newNo) != newLevel) {
            throw new ApiException("VALIDATION_ERROR",
                    "lessonNo " + newNo + " không thuộc cấp độ " + newLevel + ".",
                    HttpStatus.BAD_REQUEST);
        }
        if (newLevel != lesson.getLevel() || newNo != lesson.getLessonNo()) {
            lessonRepository.findByLevelAndLessonNo(newLevel, newNo).ifPresent(l -> {
                throw new ApiException("LESSON_EXISTS",
                        "Đã có bài " + newLevel + " số " + newNo + ".", HttpStatus.CONFLICT);
            });
        }
        lesson.setLevel(newLevel);
        lesson.setLessonNo(newNo);
        if (req.titleJa() != null && !req.titleJa().isBlank()) {
            lesson.setTitleJa(req.titleJa().trim());
        }
        if (req.titleVi() != null && !req.titleVi().isBlank()) {
            // R1: strip server-side — phòng admin nhập thừa "Bài 3 — ..." vào ô tiêu đề.
            lesson.setTitleVi(stripLessonPrefix(req.titleVi().trim()));
        }
        if (req.order() != null) {
            lesson.setOrder(req.order());
        }
        lessonRepository.save(lesson);
        auditService.log(adminId, "UPDATE_LESSON", "lessons", lesson.getId(),
                Map.of("before", before,
                        "after", lesson.getLevel() + " #" + lesson.getLessonNo() + " " + lesson.getTitleVi()));
        return toDetail(lesson);
    }

    public void deleteLesson(String adminId, String id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ApiException("LESSON_NOT_FOUND",
                        "Bài học không tồn tại.", HttpStatus.NOT_FOUND));
        // Xoa mem cac tu cua bai de khong vo word_progress / custom_sets
        List<VocabWord> words = vocabWordRepository.findByLessonIdAndActiveTrueOrderByOrderInLessonAsc(id);
        for (VocabWord w : words) {
            w.setActive(false);
        }
        if (!words.isEmpty()) {
            vocabWordRepository.saveAll(words);
        }
        lessonRepository.delete(lesson);
        auditService.log(adminId, "DELETE_LESSON", "lessons", id,
                Map.of("before", lesson.getLevel() + " #" + lesson.getLessonNo(),
                        "softDeletedWords", words.size()));
    }

    private LessonDtos.LessonDetailDto toDetail(Lesson l) {
        return new LessonDtos.LessonDetailDto(l.getId(), l.getLevel().name(),
                l.getLessonNo(), l.getTitleJa(), l.getTitleVi(), l.getOrder());
    }

    // ==================== Words (F-031) ====================

    public CommonDtos.PageResponse<WordDtos.AdminWordDto> listWords(String lessonId, String q, int page) {
        List<Criteria> ands = new ArrayList<>();
        if (lessonId != null && !lessonId.isBlank()) {
            ands.add(Criteria.where("lessonId").is(lessonId.trim()));
        }
        if (q != null && !q.isBlank()) {
            String rx = Pattern.quote(q.trim());
            ands.add(new Criteria().orOperator(
                    Criteria.where("kana").regex(rx, "i"),
                    Criteria.where("kanji").regex(rx, "i"),
                    Criteria.where("romaji").regex(rx, "i"),
                    Criteria.where("meaningVi").regex(rx, "i")));
        }
        Query query = ands.isEmpty() ? new Query()
                : new Query(new Criteria().andOperator(ands.toArray(new Criteria[0])));
        long total = mongoTemplate.count(query, VocabWord.class);
        query.with(PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by("lessonId").ascending().and(Sort.by("orderInLesson").ascending())));
        List<VocabWord> words = mongoTemplate.find(query, VocabWord.class);

        Map<String, Integer> lessonNoById = lessonRepository.findAll().stream()
                .collect(Collectors.toMap(Lesson::getId, Lesson::getLessonNo, (a, b) -> a));
        List<WordDtos.AdminWordDto> items = words.stream()
                .map(w -> new WordDtos.AdminWordDto(w.getId(), w.getLessonId(),
                        lessonNoById.getOrDefault(w.getLessonId(), 0),
                        w.getSection().name(), w.getKana(), w.getKanji(), w.getRomaji(),
                        w.getMeaningVi(), w.getPos(), w.getExampleJa(), w.getExampleVi(),
                        w.getAudioUrl(), w.getOrderInLesson(), w.isActive(), null, w.getCluster()))
                .toList();
        return new CommonDtos.PageResponse<>(items, total, Math.max(page, 0), PAGE_SIZE);
    }

    public WordDtos.WordDto createWord(String adminId, WordDtos.CreateWordRequest req) {
        Lesson lesson = lessonRepository.findById(req.lessonId())
                .orElseThrow(() -> new ApiException("LESSON_NOT_FOUND",
                        "Bài học không tồn tại.", HttpStatus.NOT_FOUND));
        WordSection section = EnumUtils.parse(WordSection.class, req.section(), "section");

        VocabWord word = new VocabWord();
        word.setLessonId(lesson.getId());
        word.setSection(section);
        word.setKana(req.kana().trim());
        word.setKanji(blankToNull(req.kanji()));
        word.setRomaji(req.romaji().trim());
        word.setMeaningVi(req.meaningVi().trim());
        word.setPos(blankToNull(req.pos()));
        word.setExampleJa(blankToNull(req.exampleJa()));
        word.setExampleVi(blankToNull(req.exampleVi()));
        word.setOrderInLesson(req.orderInLesson());
        // F-A: admin co the gan cum ngay khi tao; khong nhap -> "khac".
        String reqCluster = req.cluster() == null ? null : ClusterLabels.normalize(req.cluster());
        if (reqCluster != null && !ClusterLabels.isKnown(reqCluster)) {
            throw new ApiException("CLUSTER_INVALID",
                    "Mã cụm không hợp lệ: \"" + req.cluster().trim() + "\".",
                    HttpStatus.BAD_REQUEST);
        }
        word.setCluster(reqCluster == null ? ClusterLabels.FALLBACK_CLUSTER : reqCluster);
        // Ten file audio deterministic (b{lessonNo:02d}-{order:03d}.mp3) — khop seed + script sinh audio.
        word.setAudioUrl(AudioFileUtils.audioUrl(lesson.getLessonNo(), req.orderInLesson()));
        word.setSearchNorm(VietnameseUtils.buildSearchNorm(word));
        vocabWordRepository.save(word);

        auditService.log(adminId, "CREATE_WORD", "vocab_words", word.getId(),
                Map.of("after", word.getKana() + " — " + word.getMeaningVi(),
                        "lesson", lesson.getLevel() + " #" + lesson.getLessonNo()));
        return WordMapper.toDto(word, null);
    }

    public WordDtos.WordDto updateWord(String adminId, String id, WordDtos.UpdateWordRequest req) {
        VocabWord word = vocabWordRepository.findById(id)
                .orElseThrow(() -> new ApiException("WORD_NOT_FOUND",
                        "Từ vựng không tồn tại.", HttpStatus.NOT_FOUND));
        String beforeKana = word.getKana();
        String beforeKanji = word.getKanji();
        int beforeOrder = word.getOrderInLesson();

        Map<String, Object> changes = new LinkedHashMap<>();
        if (req.section() != null) {
            WordSection section = EnumUtils.parse(WordSection.class, req.section(), "section");
            if (section != word.getSection()) {
                changes.put("section", word.getSection() + " -> " + section);
                word.setSection(section);
            }
        }
        if (req.kana() != null && !req.kana().isBlank() && !req.kana().trim().equals(word.getKana())) {
            changes.put("kana", word.getKana() + " -> " + req.kana().trim());
            word.setKana(req.kana().trim());
        }
        if (req.kanji() != null && !Objects.equals(req.kanji().trim(), word.getKanji())) {
            changes.put("kanji", word.getKanji() + " -> " + blankToNull(req.kanji()));
            word.setKanji(blankToNull(req.kanji()));
        }
        if (req.romaji() != null && !req.romaji().isBlank()) {
            word.setRomaji(req.romaji().trim());
        }
        if (req.meaningVi() != null && !req.meaningVi().isBlank()) {
            changes.put("meaningVi", word.getMeaningVi() + " -> " + req.meaningVi().trim());
            word.setMeaningVi(req.meaningVi().trim());
        }
        if (req.pos() != null) {
            word.setPos(blankToNull(req.pos()));
        }
        if (req.exampleJa() != null) {
            word.setExampleJa(blankToNull(req.exampleJa()));
        }
        if (req.exampleVi() != null) {
            word.setExampleVi(blankToNull(req.exampleVi()));
        }
        if (req.orderInLesson() != null) {
            word.setOrderInLesson(req.orderInLesson());
        }
        // F-A: admin sua cum chu de cua tu (du lieu cu chua co cluster -> nhap moi duoc).
        if (req.cluster() != null) {
            String normalized = ClusterLabels.normalize(req.cluster());
            if (!ClusterLabels.isKnown(normalized)) {
                throw new ApiException("CLUSTER_INVALID",
                        "Mã cụm không hợp lệ: \"" + req.cluster().trim() + "\".",
                        HttpStatus.BAD_REQUEST);
            }
            if (!Objects.equals(normalized, word.getCluster())) {
                changes.put("cluster", word.getCluster() + " -> " + normalized);
                word.setCluster(normalized);
            }
        }
        // Doi orderInLesson -> ten file audio deterministic doi theo -> cap nhat audioUrl.
        if (req.orderInLesson() != null && req.orderInLesson() != beforeOrder) {
            changes.put("orderInLesson", beforeOrder + " -> " + req.orderInLesson());
            String newAudioUrl = AudioFileUtils.audioUrl(lessonNoOf(word), req.orderInLesson());
            changes.put("audioUrl", word.getAudioUrl() + " -> " + newAudioUrl);
            word.setAudioUrl(newAudioUrl);
        }
        word.setSearchNorm(VietnameseUtils.buildSearchNorm(word));
        vocabWordRepository.save(word);

        auditService.log(adminId, "UPDATE_WORD", "vocab_words", id,
                changes.isEmpty() ? Map.of("note", "no changes") : changes);

        // US-010: sua kana/kanji (hoac doi ten file audio) -> tao lai audio cho tu do (job nen)
        if (!word.getKana().equals(beforeKana) || !Objects.equals(word.getKanji(), beforeKanji)
                || (req.orderInLesson() != null && req.orderInLesson() != beforeOrder)) {
            audioJobService.regenerate(List.of(word.getId()));
        }
        return WordMapper.toDto(word, null);
    }

    /** Xoa mem (FR-031): active=false de khong vo du lieu hoc vien. */
    public void deleteWord(String adminId, String id) {
        VocabWord word = vocabWordRepository.findById(id)
                .orElseThrow(() -> new ApiException("WORD_NOT_FOUND",
                        "Từ vựng không tồn tại.", HttpStatus.NOT_FOUND));
        word.setActive(false);
        vocabWordRepository.save(word);
        auditService.log(adminId, "DELETE_WORD", "vocab_words", id,
                Map.of("before", word.getKana() + " — " + word.getMeaningVi(), "active", false));
    }

    // ==================== Import Excel (F-032) ====================

    /**
     * Buoc 1 — validate: doc file, bao cao {totalRows, validRows, errors[]}
     * theo tung dong Excel. Chua ghi DB.
     */
    public WordDtos.ImportValidateResponse validateImport(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("IMPORT_EMPTY", "File Excel trống.", HttpStatus.BAD_REQUEST);
        }
        List<WordDtos.RowError> errors = new ArrayList<>();
        List<WordDtos.WordRowDto> validRows = new ArrayList<>();
        int totalRows = 0;
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new ApiException("IMPORT_BAD_HEADER",
                        "File Excel thiếu dòng tiêu đề.", HttpStatus.BAD_REQUEST);
            }
            Map<String, Integer> cols = mapHeader(header);
            for (String required : List.of("lessonno", "section", "kana", "romaji", "meaningvi")) {
                if (!cols.containsKey(required)) {
                    throw new ApiException("IMPORT_BAD_HEADER",
                            "Thiếu cột bắt buộc: " + required + ".", HttpStatus.BAD_REQUEST);
                }
            }
            DataFormatter formatter = new DataFormatter();
            Set<String> seenInFile = new HashSet<>();
            Map<Integer, Lesson> lessonCache = new HashMap<>();
            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row, cols, formatter)) {
                    continue;
                }
                totalRows++;
                int excelRow = i + 1;
                String lessonNoStr = cellValue(cols, row, "lessonno", formatter);
                int lessonNo;
                try {
                    lessonNo = Integer.parseInt(lessonNoStr);
                } catch (NumberFormatException e) {
                    errors.add(new WordDtos.RowError(excelRow, "lessonNo phải là số nguyên."));
                    continue;
                }
                if (lessonNo < 1 || lessonNo > 50) {
                    errors.add(new WordDtos.RowError(excelRow, "lessonNo phải từ 1 đến 50."));
                    continue;
                }
                Lesson lesson = lessonCache.computeIfAbsent(lessonNo,
                        n -> lessonRepository.findByLevelAndLessonNo(Level.forLessonNo(n), n).orElse(null));
                if (lesson == null) {
                    errors.add(new WordDtos.RowError(excelRow,
                            "Chưa có bài học số " + lessonNo + " trong hệ thống."));
                    continue;
                }
                String sectionStr = cellValue(cols, row, "section", formatter);
                WordSection section;
                try {
                    section = WordSection.valueOf(sectionStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    errors.add(new WordDtos.RowError(excelRow,
                            "section phải là VOCAB, REFERENCE hoặc PHRASE."));
                    continue;
                }
                String kana = cellValue(cols, row, "kana", formatter);
                String romaji = cellValue(cols, row, "romaji", formatter);
                String meaningVi = cellValue(cols, row, "meaningvi", formatter);
                if (kana.isEmpty() || romaji.isEmpty() || meaningVi.isEmpty()) {
                    errors.add(new WordDtos.RowError(excelRow,
                            "kana, romaji và nghĩa tiếng Việt không được để trống."));
                    continue;
                }
                String dupKey = lesson.getId() + "|" + section + "|" + kana;
                if (!seenInFile.add(dupKey)) {
                    errors.add(new WordDtos.RowError(excelRow,
                            "Trùng từ trong file (cùng bài, cùng phần, cùng kana)."));
                    continue;
                }
                // Dong hop le: giu du lieu de FE gui lai o buoc confirm (khong can importToken).
                validRows.add(new WordDtos.WordRowDto(
                        lessonNo,
                        section.name(),
                        kana,
                        blankToNull(cellValue(cols, row, "kanji", formatter)),
                        romaji,
                        meaningVi,
                        blankToNull(cellValue(cols, row, "pos", formatter)),
                        blankToNull(cellValue(cols, row, "exampleja", formatter)),
                        blankToNull(cellValue(cols, row, "examplevi", formatter))));
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("IMPORT_READ_FAILED",
                    "Không đọc được file Excel. Hãy dùng đúng file mẫu.", HttpStatus.BAD_REQUEST);
        }
        return new WordDtos.ImportValidateResponse(totalRows, validRows.size(), errors, validRows);
    }

    /**
     * Buoc 2 — confirm: ghi DB. Upsert theo (lessonId, section, kana):
     * co roi -> cap nhat, chua -> tao moi.
     */
    public WordDtos.ImportConfirmResponse confirmImport(String adminId, List<WordDtos.WordRowDto> rows) {
        int imported = 0;
        int skipped = 0;
        Map<Integer, Lesson> lessonCache = new HashMap<>();
        Map<String, Integer> nextOrderByLesson = new HashMap<>();
        for (WordDtos.WordRowDto row : rows) {
            try {
                if (row.lessonNo() == null || row.lessonNo() < 1 || row.lessonNo() > 50) {
                    skipped++;
                    continue;
                }
                int lessonNo = row.lessonNo();
                Lesson lesson = lessonCache.computeIfAbsent(lessonNo,
                        n -> lessonRepository.findByLevelAndLessonNo(Level.forLessonNo(n), n).orElse(null));
                if (lesson == null) {
                    skipped++;
                    continue;
                }
                WordSection section = WordSection.valueOf(row.section().trim().toUpperCase());
                String kana = row.kana().trim();
                if (kana.isEmpty() || row.romaji() == null || row.romaji().isBlank()
                        || row.meaningVi() == null || row.meaningVi().isBlank()) {
                    skipped++;
                    continue;
                }
                VocabWord word = vocabWordRepository
                        .findByLessonIdAndSectionAndKanaAndActiveTrue(lesson.getId(), section, kana)
                        .orElse(null);
                boolean isNew = word == null;
                if (isNew) {
                    word = new VocabWord();
                    word.setLessonId(lesson.getId());
                    word.setSection(section);
                    word.setKana(kana);
                    // Tu moi: thu tu noi tiep sau tu cuoi cua bai -> ten file audio deterministic.
                    int nextOrder = nextOrderByLesson.computeIfAbsent(lesson.getId(), lid ->
                            vocabWordRepository.findTopByLessonIdOrderByOrderInLessonDesc(lid)
                                    .map(VocabWord::getOrderInLesson).orElse(0) + 1);
                    word.setOrderInLesson(nextOrder);
                    nextOrderByLesson.put(lesson.getId(), nextOrder + 1);
                    word.setAudioUrl(AudioFileUtils.audioUrl(lesson.getLessonNo(), nextOrder));
                }
                word.setRomaji(row.romaji().trim());
                word.setMeaningVi(row.meaningVi().trim());
                word.setKanji(blankToNull(row.kanji()));
                word.setPos(blankToNull(row.pos()));
                word.setExampleJa(blankToNull(row.exampleJa()));
                word.setExampleVi(blankToNull(row.exampleVi()));
                word.setActive(true);
                if (isNew) {
                    word.setCluster(ClusterLabels.FALLBACK_CLUSTER); // F-A: tu import moi mac dinh cum "khac"
                }
                word.setSearchNorm(VietnameseUtils.buildSearchNorm(word));
                vocabWordRepository.save(word);
                imported++;
            } catch (Exception e) {
                skipped++;
            }
        }
        auditService.log(adminId, "IMPORT_EXCEL", "vocab_words", null,
                Map.of("imported", imported, "skipped", skipped, "totalRows", rows.size()));
        return new WordDtos.ImportConfirmResponse(imported, skipped);
    }

    private Map<String, Integer> mapHeader(Row header) {
        Map<String, Integer> cols = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : header) {
            String name = formatter.formatCellValue(cell).trim().toLowerCase();
            if (!name.isEmpty()) {
                cols.putIfAbsent(name, cell.getColumnIndex());
            }
        }
        return cols;
    }

    private String cellValue(Map<String, Integer> cols, Row row, String name, DataFormatter formatter) {
        Integer idx = cols.get(name);
        if (idx == null) {
            return "";
        }
        Cell cell = row.getCell(idx);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private boolean isBlankRow(Row row, Map<String, Integer> cols, DataFormatter formatter) {
        for (String name : List.of("lessonno", "kana", "romaji", "meaningvi")) {
            if (!cellValue(cols, row, name, formatter).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ==================== Users & Plan (F-036) ====================

    public CommonDtos.PageResponse<AdminDtos.AdminUserDto> listUsers(String q, int page) {
        Page<User> result;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        if (q != null && !q.isBlank()) {
            String rx = Pattern.quote(q.trim());
            Query query = new Query(new Criteria().orOperator(
                    Criteria.where("email").regex(rx, "i"),
                    Criteria.where("name").regex(rx, "i")));
            long total = mongoTemplate.count(query, User.class);
            query.with(pageable);
            List<User> users = mongoTemplate.find(query, User.class);
            List<AdminDtos.AdminUserDto> items = users.stream().map(this::toAdminUserDto).toList();
            return new CommonDtos.PageResponse<>(items, total, Math.max(page, 0), PAGE_SIZE);
        }
        result = userRepository.findAll(pageable);
        List<AdminDtos.AdminUserDto> items = result.getContent().stream()
                .map(this::toAdminUserDto).toList();
        return new CommonDtos.PageResponse<>(items, result.getTotalElements(), Math.max(page, 0), PAGE_SIZE);
    }

    public AdminDtos.AdminUserDto updatePlan(String adminId, String userId, AdminDtos.UpdatePlanRequest req) {
        if (adminId.equals(userId)) {
            throw new ApiException("VALIDATION_ERROR",
                    "Không thể tự thay đổi gói của chính mình.", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND",
                        "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND));
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException("VALIDATION_ERROR",
                    "Không thể đổi gói của tài khoản admin.", HttpStatus.BAD_REQUEST);
        }
        Plan plan = EnumUtils.parse(Plan.class, req.plan(), "plan");
        Plan before = user.getPlan();
        user.setPlan(plan);
        user.setPlanExpiresAt(plan == Plan.PREMIUM ? req.expiresAt() : null);
        userRepository.save(user);
        auditService.log(adminId, "UPDATE_PLAN", "users", userId,
                Map.of("before", before.name(), "after", plan.name(),
                        "expiresAt", String.valueOf(user.getPlanExpiresAt()),
                        "email", user.getEmail()));
        return toAdminUserDto(user);
    }

    private AdminDtos.AdminUserDto toAdminUserDto(User u) {
        return new AdminDtos.AdminUserDto(u.getId(), u.getEmail(), u.getName(),
                u.getRole().name(), u.getPlan().name(), u.getPlanExpiresAt(),
                u.getStreakDays(), u.getCreatedAt());
    }

    // ==================== Stats (FR-034) ====================

    public AdminDtos.StatsResponse stats() {
        long totalUsers = userRepository.count();
        long totalWords = vocabWordRepository.countByActiveTrue();
        long totalLessons = lessonRepository.count();
        long totalSessions = studySessionRepository.count();

        LocalDate today = appTime.today();
        List<AdminDtos.DayCountDto> sessionsByDay = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Date from = appTime.startOfDay(day);
            Date to = appTime.startOfDay(day.plusDays(1));
            long count = studySessionRepository.countByFinishedAtBetween(from, to);
            sessionsByDay.add(new AdminDtos.DayCountDto(day.toString(), count));
        }
        long sessionsToday = sessionsByDay.get(sessionsByDay.size() - 1).count();

        List<WordProgress> tops = wordProgressRepository
                .findTop10ByWrongCountGreaterThanOrderByWrongCountDesc(0);
        Set<String> wordIds = tops.stream().map(WordProgress::getWordId).collect(Collectors.toSet());
        Map<String, VocabWord> wordById = wordIds.isEmpty() ? Map.of()
                : vocabWordRepository.findAllById(wordIds).stream()
                .collect(Collectors.toMap(VocabWord::getId, w -> w, (a, b) -> a));
        Map<String, Integer> lessonNoById = lessonRepository.findAll().stream()
                .collect(Collectors.toMap(Lesson::getId, Lesson::getLessonNo, (a, b) -> a));
        List<AdminDtos.TopWrongWordDto> topWrongWords = tops.stream()
                .map(p -> {
                    VocabWord w = wordById.get(p.getWordId());
                    if (w == null) {
                        return null;
                    }
                    return new AdminDtos.TopWrongWordDto(w.getId(), w.getKana(), w.getKanji(),
                            w.getRomaji(), w.getMeaningVi(),
                            lessonNoById.getOrDefault(w.getLessonId(), 0),
                            p.getWrongCount());
                })
                .filter(Objects::nonNull)
                .toList();

        return new AdminDtos.StatsResponse(totalUsers, totalWords, totalLessons,
                totalSessions, sessionsToday, sessionsByDay, topWrongWords);
    }

    // ==================== Audit logs ====================

    public CommonDtos.PageResponse<AdminDtos.AuditLogDto> listAuditLogs(int page) {
        Page<AuditLog> result = auditLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        Set<String> adminIds = result.getContent().stream()
                .map(AuditLog::getAdminId).collect(Collectors.toSet());
        Map<String, String> emailById = userRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        List<AdminDtos.AuditLogDto> items = result.getContent().stream()
                .map(l -> new AdminDtos.AuditLogDto(l.getId(), l.getAdminId(),
                        emailById.getOrDefault(l.getAdminId(), "?"),
                        l.getAction(), l.getTargetType(), l.getTargetId(),
                        l.getDiff(), l.getCreatedAt()))
                .toList();
        return new CommonDtos.PageResponse<>(items, result.getTotalElements(), Math.max(page, 0), PAGE_SIZE);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Lay lessonNo cua bai chua tu (phuc vu ten file audio deterministic). */
    private int lessonNoOf(VocabWord word) {
        return lessonRepository.findById(word.getLessonId())
                .orElseThrow(() -> new ApiException("LESSON_NOT_FOUND",
                        "Bài học của từ không tồn tại.", HttpStatus.NOT_FOUND))
                .getLessonNo();
    }
}
