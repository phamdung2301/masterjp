package com.masterjp.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase B — Sensei AI: quan ly Groq API keys + round-robin.
 * <p>
 * Thu thap moi property khac blank trong {@code GROQ_API_KEY}, {@code GROQ_API_KEY_2} …
 * {@code GROQ_API_KEY_6} (ENV OS hoac backend/.env — ca hai deu qua
 * {@link Environment#getProperty}). Khong co key nao -> <b>fail fast luc startup</b>
 * (IllegalStateException) thay vi disable im lang.
 * <p>
 * Thread-safe: rotation dung {@link AtomicInteger}; key bi provider tu choi
 * (401/403) duoc danh dau "bad" 10 phut qua {@link #markBad(String)} va bi
 * bo qua khi chon key (tru khi tat ca deu bad).
 * <p>
 * Khong dung static — inject qua constructor de test-writer co the new truc tiep
 * voi Environment gia.
 */
@Component
public class GroqKeyProvider {

    /** Mot key Groq: label de log (KHONG BAO GIO log value). */
    public record GroqKey(String label, String value) {
    }

    static final int MAX_KEYS = 6;
    static final Duration BAD_KEY_COOLDOWN = Duration.ofMinutes(10);

    private final List<GroqKey> keys;
    private final AtomicInteger cursor = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Long> badUntilMillis = new ConcurrentHashMap<>();

    public GroqKeyProvider(Environment env) {
        List<GroqKey> collected = new ArrayList<>();
        for (int i = 1; i <= MAX_KEYS; i++) {
            String name = (i == 1) ? "GROQ_API_KEY" : "GROQ_API_KEY_" + i;
            String value = env.getProperty(name);
            if (value != null && !value.isBlank()) {
                collected.add(new GroqKey(name, value.trim()));
            }
        }
        if (collected.isEmpty()) {
            throw new IllegalStateException(
                    "GROQ_API_KEY chua duoc cau hinh — Sensei AI la feature loi cua Phase B, "
                            + "tu choi khoi dong thay vi chay im lang. "
                            + "Hay dat GROQ_API_KEY (va tuy chon GROQ_API_KEY_2..6, OPENROUTER_API_KEY) "
                            + "qua ENV hoac backend/.env roi restart backend.");
        }
        this.keys = List.copyOf(collected);
    }

    /** So key kha dung (de AiService biet thu toi da bao nhieu key moi request). */
    public int keyCount() {
        return keys.size();
    }

    /**
     * Key tiep theo trong vong xoay round-robin. Bo qua key dang "bad"
     * (bi 401/403 trong 10 phut gan nhat); neu tat ca deu bad thi van tra
     * key theo vong xoay (thu lai, khong bo request).
     */
    public GroqKey nextKey() {
        int start = Math.floorMod(cursor.getAndIncrement(), keys.size());
        long now = System.currentTimeMillis();
        for (int i = 0; i < keys.size(); i++) {
            GroqKey k = keys.get((start + i) % keys.size());
            Long until = badUntilMillis.get(k.label());
            if (until == null || until <= now) {
                return k;
            }
        }
        return keys.get(start);
    }

    /** Danh dau key "bad" 10 phut sau khi provider tra 401/403 (log label, KHONG log value). */
    public void markBad(String label) {
        badUntilMillis.put(label, System.currentTimeMillis() + BAD_KEY_COOLDOWN.toMillis());
    }

    /** Test seam: key co dang bi danh dau bad khong. */
    boolean isBad(String label) {
        Long until = badUntilMillis.get(label);
        return until != null && until > System.currentTimeMillis();
    }
}
