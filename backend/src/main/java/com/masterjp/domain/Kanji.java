package com.masterjp.domain;

import org.springframework.data.annotation.Id;
// LUU Y (bai hoc xuong mau): import dung la
// org.springframework.data.mongodb.core.mapping.Document,
// KHONG phai core.document — sai package nay tung lam sap build Docker.
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.util.ArrayList;
import java.util.List;

/**
 * F-B (Feature Pack 2): bo data kanji hoc rieng.
 * id = chinh character (vd. "私") -> import idempotent, khong trung lap.
 */
@Document("kanji")
@CompoundIndexes({
        @CompoundIndex(name = "idx_kanji_lesson", def = "{'lessonNos': 1}"),
        @CompoundIndex(name = "idx_kanji_jlpt", def = "{'jlpt': 1}"),
        @CompoundIndex(name = "idx_kanji_topic", def = "{'topic': 1}"),
        // Phase 1 (N3/N2): filter ket hop cap do + chu de
        @CompoundIndex(name = "idx_kanji_jlpt_topic", def = "{'jlpt': 1, 'topic': 1}")
})
public class Kanji {

    @Id
    private String id; // = character

    private String character; // vd. "私"
    private List<String> onYomi = new ArrayList<>(); // vd. ["シ"]
    private List<String> kunYomi = new ArrayList<>(); // vd. ["わたし", "わたくし"]
    private String meaningVi; // nghia tieng Viet
    private int strokes; // so net
    private String jlpt; // "N5" / "N4" / "N3" / "N2"
    private List<Integer> lessonNos = new ArrayList<>(); // cac bai co tu chua chu nay
    private String topic; // ma chu de (vd. "thoi_gian") — xem KanjiService.TOPIC_LABELS
    /** URL audio doc chu kanji (R2 object storage); null neu chua generate. */
    private String audioUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCharacter() {
        return character;
    }

    public void setCharacter(String character) {
        this.character = character;
    }

    public List<String> getOnYomi() {
        return onYomi;
    }

    public void setOnYomi(List<String> onYomi) {
        this.onYomi = onYomi;
    }

    public List<String> getKunYomi() {
        return kunYomi;
    }

    public void setKunYomi(List<String> kunYomi) {
        this.kunYomi = kunYomi;
    }

    public String getMeaningVi() {
        return meaningVi;
    }

    public void setMeaningVi(String meaningVi) {
        this.meaningVi = meaningVi;
    }

    public int getStrokes() {
        return strokes;
    }

    public void setStrokes(int strokes) {
        this.strokes = strokes;
    }

    public String getJlpt() {
        return jlpt;
    }

    public void setJlpt(String jlpt) {
        this.jlpt = jlpt;
    }

    public List<Integer> getLessonNos() {
        return lessonNos;
    }

    public void setLessonNos(List<Integer> lessonNos) {
        this.lessonNos = lessonNos;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }
}
