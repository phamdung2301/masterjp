package com.masterjp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document("lessons")
@CompoundIndex(name = "uq_level_lessonNo", def = "{'level': 1, 'lessonNo': 1}", unique = true)
public class Lesson {

    @Id
    private String id;

    private Level level; // N5 / N4 / N3 / N2
    private int lessonNo; // 1-25 (N5), 26-50 (N4), 51-70 (N3), 71-90 (N2)
    /**
     * Giao trinh nguon: "MINNA" (bai 1-50) / "SHINKANZEN" (bai 51+).
     * Default "MINNA" cho data cu.
     */
    private String source = "MINNA";
    private String titleJa; // vd. 第1課
    /**
     * R1 contract: titleVi KHÔNG chứa prefix "Bài N — " — chỉ là tên bài,
     * vd. "Chào hỏi, giới thiệu". Prefix "Bài {lessonNo} — " được frontend
     * thêm lúc hiển thị. AdminService.createLesson/updateLesson strip mọi
     * prefix bị nhập thừa để không bao giờ lưu "Bài 1 — Bài 1 — ..." vào DB.
     */
    private String titleVi;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
