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
 * Feature: Ngu phap — v1 read-only, public GET (giong kanji v1).
 * id on dinh dang "g{lessonNo}-NN" (vd. "g01-01") de seed idempotent.
 * "level" luu dang String ("N5"/"N4"), khong dung enum (theo cach jlpt cua Kanji).
 */
@Document("grammar")
@CompoundIndexes({
        @CompoundIndex(name = "idx_grammar_lesson", def = "{'lessonNo': 1, 'orderInLesson': 1}")
})
public class Grammar {

    @Id
    private String id; // vd. "g01-01"

    private String lessonId; // hex string, KHONG ObjectId (convention codebase)
    private int lessonNo; // denormalized, 1..50
    private String level; // "N5" / "N4"
    private int orderInLesson; // thu tu trong bai

    private String title; // vd. "V-てください"
    private String titleVi; // vd. "Hãy làm ~ (nhờ vả, yêu cầu)"
    private String pattern; // vd. "V-て + ください"
    private String meaningVi; // y nghia tong quat
    private String explanation; // giai thich chi tiet bang tieng Viet
    private String formation; // cach chia / cong thuc lap
    private List<Example> examples = new ArrayList<>(); // 2-3 cau vi du
    private String notes; // loi thuong gap / diem can luu y
    private List<String> compareWith = new ArrayList<>(); // id cac diem ngu phap de nham, vd. ["g15-01"]
    private List<String> tags = new ArrayList<>(); // vd. ["yeu-cau", "te-form"]

    /** Xoa mem (theo convention): false = an khoi moi truy van cong khai. */
    private boolean active = true;

    /** Chuoi tim kiem da chuan hoa (title + titleVi bo dau + pattern + meaningVi),
     *  phuc vu search "go khong dau van ra". */
    private String searchNorm;

    /** Vi du su dung — nested POJO, Mongo map duoc binh thuong. */
    public static class Example {
        private String ja;
        private String kana;
        private String vi;

        public String getJa() {
            return ja;
        }

        public void setJa(String ja) {
            this.ja = ja;
        }

        public String getKana() {
            return kana;
        }

        public void setKana(String kana) {
            this.kana = kana;
        }

        public String getVi() {
            return vi;
        }

        public void setVi(String vi) {
            this.vi = vi;
        }
    }

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

    public int getLessonNo() {
        return lessonNo;
    }

    public void setLessonNo(int lessonNo) {
        this.lessonNo = lessonNo;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public int getOrderInLesson() {
        return orderInLesson;
    }

    public void setOrderInLesson(int orderInLesson) {
        this.orderInLesson = orderInLesson;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleVi() {
        return titleVi;
    }

    public void setTitleVi(String titleVi) {
        this.titleVi = titleVi;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getMeaningVi() {
        return meaningVi;
    }

    public void setMeaningVi(String meaningVi) {
        this.meaningVi = meaningVi;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getFormation() {
        return formation;
    }

    public void setFormation(String formation) {
        this.formation = formation;
    }

    public List<Example> getExamples() {
        return examples;
    }

    public void setExamples(List<Example> examples) {
        this.examples = examples;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<String> getCompareWith() {
        return compareWith;
    }

    public void setCompareWith(List<String> compareWith) {
        this.compareWith = compareWith;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
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
}
