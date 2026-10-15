package com.masterjp.util;

/**
 * Quy uoc ten file audio DETERMINISTIC — khop seed (data/seed) va script
 * sinh audio (scripts/generate-audio.py):
 *   b{lessonNo:02d}-{orderInLesson:03d}.mp3  (vd. b01-001.mp3, b26-042.mp3)
 * DB chi luu audioUrl dang "/audio/{audioFile}".
 * KHONG dung ObjectId lam ten file — khong doan duoc, lech voi seed.
 */
public final class AudioFileUtils {

    private AudioFileUtils() {
    }

    public static String audioFile(int lessonNo, int orderInLesson) {
        return String.format("b%02d-%03d.mp3", lessonNo, orderInLesson);
    }

    public static String audioUrl(int lessonNo, int orderInLesson) {
        return "/audio/" + audioFile(lessonNo, orderInLesson);
    }
}
