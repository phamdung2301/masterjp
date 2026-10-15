package com.masterjp.util;

import java.util.Map;

/**
 * F-A (Feature Pack 2): bang ma cum chu de -> ten hien thi tieng Viet.
 * Cum la thuoc tinh cua tu (field VocabWord.cluster), KHONG co collection
 * rieng — label suy tu code bang bang nay. Ma la khong co trong bang
 * (du lieu cu / ma moi) -> fallback ve chinh ma do.
 */
public final class ClusterLabels {

    /** Ma danh cho tu khong xep duoc vao cum nao (spec F-A). */
    public static final String FALLBACK_CLUSTER = "khac";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("gia-dinh", "Gia đình"),
            Map.entry("con-nguoi", "Con người"),
            Map.entry("nghe-nghiep", "Nghề nghiệp"),
            Map.entry("do-dung", "Đồ dùng"),
            Map.entry("thoi-gian", "Thời gian"),
            Map.entry("dia-diem", "Địa điểm"),
            Map.entry("an-uong", "Ăn uống"),
            Map.entry("dong-vat", "Động vật"),
            Map.entry("thien-nhien", "Thiên nhiên & thời tiết"),
            Map.entry("co-the", "Cơ thể"),
            Map.entry("suc-khoe", "Sức khỏe"),
            Map.entry("quan-ao", "Quần áo"),
            Map.entry("mau-sac", "Màu sắc"),
            Map.entry("so-dem", "Số đếm"),
            Map.entry("giao-thong", "Giao thông"),
            Map.entry("hoc-tap", "Học tập"),
            Map.entry("cong-viec", "Công việc"),
            Map.entry("mua-sam", "Mua sắm"),
            Map.entry("tien-bac", "Tiền bạc"),
            Map.entry("du-lich", "Du lịch"),
            Map.entry("cam-xuc", "Cảm xúc & tính cách"),
            Map.entry("tinh-chat", "Tính chất"),
            Map.entry("hanh-dong", "Hành động"),
            Map.entry("the-thao", "Thể thao"),
            Map.entry("giai-tri", "Giải trí"),
            Map.entry("khoa-hoc", "Khoa học"),
            Map.entry("vi-tri", "Vị trí & hướng"),
            Map.entry("giao-tiep", "Giao tiếp"),
            Map.entry("khac", "Khác"));

    private ClusterLabels() {
    }

    /** Ten hien thi cua ma cum; null/blank/ma la -> ten cua "khac". */
    public static String labelOf(String cluster) {
        if (cluster == null || cluster.isBlank()) {
            return LABELS.get(FALLBACK_CLUSTER);
        }
        return LABELS.getOrDefault(cluster, cluster);
    }

    /** Chuan hoa gia tri cluster luu DB: null/blank -> "khac", trim + lowercase. */
    public static String normalize(String cluster) {
        if (cluster == null || cluster.isBlank()) {
            return FALLBACK_CLUSTER;
        }
        return cluster.trim().toLowerCase();
    }

    /** Ma cum co nam trong bang dinh nghia khong (chong typo khi admin nhap tay). */
    public static boolean isKnown(String cluster) {
        return cluster != null && LABELS.containsKey(cluster);
    }
}
