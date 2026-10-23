package com.masterjp.service;

import java.util.List;

/**
 * Phase C — F-042: catalog 12 huy hieu (binding theo DOC-12 muc 4.1).
 * Icon = ky tu Nhat (KHONG emoji). Hard-code trong backend — FE khong tu bia text.
 */
public final class BadgeCatalog {

    private BadgeCatalog() {
    }

    public record BadgeDef(String code, String icon, String name, String description) {
    }

    public static final List<BadgeDef> ALL = List.of(
            new BadgeDef("FIRST_SESSION", "始", "Khởi động", "Hoàn thành 1 phiên học bất kỳ"),
            new BadgeDef("VOCAB_100", "語", "Vốn từ 100", "Thuộc 100 từ vựng"),
            new BadgeDef("VOCAB_500", "語", "Vốn từ 500", "Thuộc 500 từ vựng"),
            new BadgeDef("STREAK_7", "復", "Tuần rực lửa", "Chuỗi 7 ngày liên tiếp"),
            new BadgeDef("STREAK_30", "復", "Tháng bền bỉ", "Chuỗi 30 ngày liên tiếp"),
            new BadgeDef("LESSONS_5", "文", "Chạy đà", "Hoàn thành 5 bài học"),
            new BadgeDef("LESSONS_25", "文", "Nửa chặng N5", "Hoàn thành 25 bài học"),
            new BadgeDef("LESSONS_50", "漢", "Chinh phục 50", "Hoàn thành cả 50 bài"),
            new BadgeDef("SHADOW_1", "影", "Luyện nói đầu tiên", "Hoàn thành 1 phiên luyện nói"),
            new BadgeDef("SENSEI_10", "師", "Học trò Sensei", "Nhờ Sensei AI giúp đỡ 10 lần"),
            new BadgeDef("REVIEW_100", "復", "Bậc thầy ôn tập", "Trả lời đúng 100 lần ôn tập"),
            new BadgeDef("NIGHT_OWL", "月", "Cú đêm chăm chỉ", "Hoàn thành 1 phiên học sau 23:00")
    );

    static {
        if (ALL.size() != 12) {
            throw new IllegalStateException("BadgeCatalog phai co dung 12 huy hieu.");
        }
    }
}
