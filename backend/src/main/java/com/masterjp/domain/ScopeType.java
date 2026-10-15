package com.masterjp.domain;

/**
 * Pham vi phien hoc. REVIEW = on tap den han SM-2 (F-026): tu gom tu nhieu bai,
 * scopeId la marker "due" (khong phai id cua entity cu the).
 * QUICK (F-C, Feature Pack 2): luyen nhanh tu danh sach tu chon tay, khong tao
 * CustomSet — scopeId la marker "quick" khi FE ghi session qua POST /api/sessions.
 */
public enum ScopeType {
    LESSON,
    SECTION,
    SET,
    REVIEW,
    QUICK
}
