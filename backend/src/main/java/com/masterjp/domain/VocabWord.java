package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;

import java.util.ArrayList;
import java.util.List;

@Document("vocab_words")
@CompoundIndexes({
        @CompoundIndex(name = "idx_lesson_order", def = "{'lessonId': 1, 'orderInLesson': 1}"),
        @CompoundIndex(name = "idx_lesson_section_kana", def = "{'lessonId': 1, 'section': 1, 'kana': 1}"),
        @CompoundIndex(name = "idx_lesson_cluster", def = "{'lessonId': 1, 'cluster': 1}"),
        // Phase 1 (N3/N2): query bai hoc vua loc active vua sort theo order
        @CompoundIndex(name = "idx_lesson_active_order", def = "{'lessonId': 1, 'active': 1, 'orderInLesson': 1}"),
        // Phase 1: prefix search case-insensitive dung duoc index (khong flag "i")
        @CompoundIndex(name = "idx_lower_search", def = "{'kanaLower': 1, 'kanjiLower': 1, 'romajiLower': 1}")
})
public class VocabWord {

    @Id
    private String id;

    @Indexed
    private String lessonId;

    /**
     * Cap do JLPT denormalized ("N5"/"N4"/"N3"/"N2") — tranh join lessons
     * khi loc tu theo cap do. Data cu (Minna) co the null; service xu ly
     * null nhu suy ra tu lessonId.
     */
    @Indexed
    private String level;

    /** Unit trong cap do (1-20 cho N3/N2 theo chu de Shin Kanzen); null cho data Minna. */
    private Integer unit;

    /** Tags chu de/ky nang, vd. ["business", "keigo"]. */
    private List<String> tags = new ArrayList<>();

    /**
     * Trang thai audio TTS batch: PENDING / DONE / FAILED.
     * Pipeline edge-tts dung de tracking, tranh re-generate mu.
     */
    private String audioStatus = "PENDING";

    private WordSection section; // VOCAB / REFERENCE / PHRASE

    @Indexed
    private String kana;

    @Indexed
    private String kanji; // null neu tu chi dung kana

    private String romaji;
    private String meaningVi; // nghia tieng Viet da duyet
    private String pos; // loai tu
    private String exampleJa;
    private String exampleVi;
    private String audioUrl; // /audio/{audioFile}.mp3 (deterministic: b{lessonNo}-{order}.mp3) — DB chi luu URL
    private int orderInLesson;

    /**
     * Cum chu de cua tu (F-A, Feature Pack 2): ma cum slug, vd. "do-dung".
     * Tu khong xep duoc vao cum nao -> "khac". Null tren du lieu cu duoc
     * xu ly nhu "khac" o tang service.
     */
    private String cluster;

    /** Xoa mem (FR-031): false = an khoi moi truy van cong khai, giu word_progress. */
    private boolean active = true;

    /**
     * Chuoi tim kiem da chuan hoa (kana + kanji + romaji + nghia Viet bo dau + pos),
     * phuc vu F-012 "go khong dau van ra". Duoc danh text index.
     */
    @TextIndexed
    private String searchNorm;

    /**
     * Ban sao lowercase cua kana/kanji/romaji — phuc vu prefix search
     * case-insensitive DUNG DUOC index (regex "^..." khong flag "i").
     * Do seed script/service dien khi save; null tren data cu.
     */
    private String kanaLower;
    private String kanjiLower;
    private String romajiLower;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLessonId() {
        return lessonId;
    }

    public void setLessonId(String lessonId) {
        this.lessonId = lessonId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Integer getUnit() {
        return unit;
    }

    public void setUnit(Integer unit) {
        this.unit = unit;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getAudioStatus() {
        return audioStatus;
    }

    public void setAudioStatus(String audioStatus) {
        this.audioStatus = audioStatus;
    }

    public String getKanaLower() {
        return kanaLower;
    }

    public void setKanaLower(String kanaLower) {
        this.kanaLower = kanaLower;
    }

    public String getKanjiLower() {
        return kanjiLower;
    }

    public void setKanjiLower(String kanjiLower) {
        this.kanjiLower = kanjiLower;
    }

    public String getRomajiLower() {
        return romajiLower;
    }

    public void setRomajiLower(String romajiLower) {
        this.romajiLower = romajiLower;
    }

    public WordSection getSection() {
        return section;
    }

    public void setSection(WordSection section) {
        this.section = section;
    }

    public String getKana() {
        return kana;
    }

    public void setKana(String kana) {
        this.kana = kana;
    }

    public String getKanji() {
        return kanji;
    }

    public void setKanji(String kanji) {
        this.kanji = kanji;
    }

    public String getRomaji() {
        return romaji;
    }

    public void setRomaji(String romaji) {
        this.romaji = romaji;
    }

    public String getMeaningVi() {
        return meaningVi;
    }

    public void setMeaningVi(String meaningVi) {
        this.meaningVi = meaningVi;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public String getExampleJa() {
        return exampleJa;
    }

    public void setExampleJa(String exampleJa) {
        this.exampleJa = exampleJa;
    }

    public String getExampleVi() {
        return exampleVi;
    }

    public void setExampleVi(String exampleVi) {
        this.exampleVi = exampleVi;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public int getOrderInLesson() {
        return orderInLesson;
    }

    public void setOrderInLesson(int orderInLesson) {
        this.orderInLesson = orderInLesson;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSearchNorm() {
        return searchNorm;
    }

    public void setSearchNorm(String searchNorm) {
        this.searchNorm = searchNorm;
    }

    /** Dang hien thi uu tien: kanji neu co, khong thi kana. */
    public String displayForm() {
        return (kanji != null && !kanji.isBlank()) ? kanji : kana;
    }
}
