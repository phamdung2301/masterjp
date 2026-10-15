package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.document.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document("lessons")
@CompoundIndex(name = "uq_level_lessonNo", def = "{'level': 1, 'lessonNo': 1}", unique = true)
public class Lesson {

    @Id
    private String id;

    private Level level; // N5 / N4
    private int lessonNo; // 1-25 (N5), 26-50 (N4)
    private String titleJa; // vd. 第1課
    private String titleVi; // vd. Bài 1 — Chào hỏi, giới thiệu
    private int order; // thu tu hien thi

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public int getLessonNo() {
        return lessonNo;
    }

    public void setLessonNo(int lessonNo) {
        this.lessonNo = lessonNo;
    }

    public String getTitleJa() {
        return titleJa;
    }

    public void setTitleJa(String titleJa) {
        this.titleJa = titleJa;
    }

    public String getTitleVi() {
        return titleVi;
    }

    public void setTitleVi(String titleVi) {
        this.titleVi = titleVi;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
