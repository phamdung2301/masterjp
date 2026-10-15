package com.masterjp.domain;

public enum Level {
    N5,
    N4;

    /** lessonNo 1-25 -> N5, 26-50 -> N4. */
    public static Level forLessonNo(int lessonNo) {
        if (lessonNo >= 1 && lessonNo <= 25) {
            return N5;
        }
        if (lessonNo >= 26 && lessonNo <= 50) {
            return N4;
        }
        throw new IllegalArgumentException("lessonNo phai tu 1 den 50.");
    }
}
