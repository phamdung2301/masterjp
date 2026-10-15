package com.masterjp.util;

import com.masterjp.domain.VocabWord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit test chuan hoa tieng Viet cho F-012 (go khong dau van ra). */
class VietnameseUtilsTest {

    @Test
    void boDauTiengViet() {
        assertEquals("an", VietnameseUtils.normalize("ăn"));
        assertEquals("tieng viet", VietnameseUtils.normalize("Tiếng Việt"));
        assertEquals("duong", VietnameseUtils.normalize("đường"));
        assertEquals("chao hoi", VietnameseUtils.normalize("  Chào   HỎI "));
    }

    @Test
    void nullThanhChuoiRong() {
        assertEquals("", VietnameseUtils.normalize(null));
    }

    @Test
    void buildSearchNormGomDuTruong() {
        VocabWord w = new VocabWord();
        w.setKana("たべる");
        w.setKanji("食べる");
        w.setRomaji("taberu");
        w.setMeaningVi("ăn");
        w.setPos("động từ nhóm 1");

        String norm = VietnameseUtils.buildSearchNorm(w);
        assertTrue(norm.contains("たべる"));
        assertTrue(norm.contains("taberu"));
        assertTrue(norm.contains("an")); // "ăn" da bo dau
        assertTrue(norm.contains("dong tu nhom 1")); // pos da bo dau
    }
}
