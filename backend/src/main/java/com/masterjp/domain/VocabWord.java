package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;

@Document("vocab_words")
@CompoundIndexes({
        @CompoundIndex(name = "idx_lesson_order", def = "{'lessonId': 1, 'orderInLesson': 1}"),
        @CompoundIndex(name = "idx_lesson_section_kana", def = "{'lessonId': 1, 'section': 1, 'kana': 1}"),
        @CompoundIndex(name = "idx_lesson_cluster", def = "{'lessonId': 1, 'cluster': 1}")
})
public class VocabWord {

    @Id
    private String id;

    @Indexed
    private String lessonId;

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
