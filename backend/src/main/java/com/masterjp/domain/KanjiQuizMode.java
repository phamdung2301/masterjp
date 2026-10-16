package com.masterjp.domain;

/**
 * Track 3 (R1-R3 rebuild) — mode quiz kanji.
 *
 * LƯU Ý (bài học xương máu): enum RIÊNG, KHÔNG tái dùng {@link QuizMode}
 * của quiz từ vựng — mọi switch trên enum này đều phải exhaustive (xem
 * KanjiQuizService#promptText / #optionText); tái dùng QuizMode sẽ vỡ
 * switch exhaustive ở QuizService khi thêm case.
 */
public enum KanjiQuizMode {
    /** Hiện chữ kanji → chọn nghĩa Việt (4 đáp án). */
    KANJI_MEANING,
    /** Hiện nghĩa Việt → chọn chữ kanji (4 đáp án). */
    MEANING_KANJI,
    /** Hiện chữ + nghĩa → gõ cách đọc; BE trả mọi on/kun, FE chấm local. */
    KANJI_TYPING
}
