package com.masterjp.util;

import com.masterjp.domain.VocabWord;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chuan hoa tieng Viet phuc vu tim kiem "go khong dau van ra" (F-012):
 * bo dau thanh/nang, đ->d, lowercase, gon khoang trang.
 */
public final class VietnameseUtils {

    private VietnameseUtils() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String n = Normalizer.normalize(input, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}", ""); // bo dau to hop
        n = n.replace('đ', 'd').replace('Đ', 'd');
        n = n.toLowerCase(Locale.ROOT).trim();
        return n.replaceAll("\\s+", " ");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Chuoi tim kiem cua 1 tu: kana + kanji + romaji + nghia Viet (bo dau) + loai tu. */
    public static String buildSearchNorm(VocabWord word) {
        return normalize(String.join(" ",
                nz(word.getKana()),
                nz(word.getKanji()),
                nz(word.getRomaji()),
                nz(word.getMeaningVi()),
                nz(word.getPos())));
    }
}
