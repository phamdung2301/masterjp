package com.masterjp.domain;

public enum Level {
    N5,
    N4,
    N3,
    N2;

    /**
     * lessonNo 1-25 -> N5 (Minna), 26-50 -> N4 (Minna),
     * 51-70 -> N3 (Shin Kanzen), 71-90 -> N2 (Shin Kanzen).
     */
    public static Level forLessonNo(int lessonNo) {
        if (lessonNo >= 1 && lessonNo <= 25) {
            return N5;
        }
        if (lessonNo >= 26 && lessonNo <= 50) {
            return N4;
        }
        if (lessonNo >= 51 && lessonNo <= 70) {
            return N3;
        }
        if (lessonNo >= 71 && lessonNo <= 90) {
            return N2;
        }
        throw new IllegalArgumentException("lessonNo phai tu 1 den 90.");
    }
}
