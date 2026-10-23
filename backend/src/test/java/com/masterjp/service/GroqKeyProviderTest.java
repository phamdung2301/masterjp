package com.masterjp.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test cho GroqKeyProvider (Phase B — Sensei AI).
 * Khong can Spring context, khong goi network: dung MockEnvironment (spring-test).
 * Chay bang: mvn test -Dtest=GroqKeyProviderTest
 */
class GroqKeyProviderTest {

    private MockEnvironment env(String... pairs) {
        MockEnvironment e = new MockEnvironment();
        for (int i = 0; i < pairs.length; i += 2) {
            e.setProperty(pairs[i], pairs[i + 1]);
        }
        return e;
    }

    @Test
    void khongCoKeyNao_nemIllegalStateExceptionNgayLucKhoiTao() {
        assertThrows(IllegalStateException.class,
                () -> new GroqKeyProvider(new MockEnvironment()));
    }

    @Test
    void keyBlankBiBoQua_chiThuThapKeyCoGiaTri() {
        GroqKeyProvider p = new GroqKeyProvider(env(
                "GROQ_API_KEY", "   ",
                "GROQ_API_KEY_2", "key-2",
                "GROQ_API_KEY_3", ""));

        assertEquals(1, p.keyCount());
        assertEquals("GROQ_API_KEY_2", p.nextKey().label());
    }

    @Test
    void giaTriKeyDuocTrim() {
        GroqKeyProvider p = new GroqKeyProvider(env("GROQ_API_KEY", "  key-1\n"));
        assertEquals("key-1", p.nextKey().value());
    }

    @Test
    void thuThapToiDa6Key_keyThu7BiBoQua() {
        GroqKeyProvider p = new GroqKeyProvider(env(
                "GROQ_API_KEY", "k1",
                "GROQ_API_KEY_2", "k2",
                "GROQ_API_KEY_3", "k3",
                "GROQ_API_KEY_4", "k4",
                "GROQ_API_KEY_5", "k5",
                "GROQ_API_KEY_6", "k6",
                "GROQ_API_KEY_7", "k7"));

        assertEquals(6, p.keyCount());
    }

    @Test
    void nextKeyXoayVongRoundRobin() {
        GroqKeyProvider p = new GroqKeyProvider(env(
                "GROQ_API_KEY", "k1",
                "GROQ_API_KEY_2", "k2",
                "GROQ_API_KEY_3", "k3"));

        assertEquals("k1", p.nextKey().value());
        assertEquals("k2", p.nextKey().value());
        assertEquals("k3", p.nextKey().value());
        assertEquals("k1", p.nextKey().value()); // quay lai dau vong
    }

    @Test
    void markBad_keyBiBoQuaTrong10Phut() {
        GroqKeyProvider p = new GroqKeyProvider(env(
                "GROQ_API_KEY", "k1",
                "GROQ_API_KEY_2", "k2"));

        p.markBad("GROQ_API_KEY");

        assertTrue(p.isBad("GROQ_API_KEY"));
        // 2 lan goi lien tiep deu phai tra key-2 (key-1 bi loai khoi vong xoay)
        assertEquals("GROQ_API_KEY_2", p.nextKey().label());
        assertEquals("GROQ_API_KEY_2", p.nextKey().label());
    }

    @Test
    void tatCaKeyDeuBad_vanTraKeyTheoVongXoay_khongBoRequest() {
        GroqKeyProvider p = new GroqKeyProvider(env(
                "GROQ_API_KEY", "k1",
                "GROQ_API_KEY_2", "k2"));

        p.markBad("GROQ_API_KEY");
        p.markBad("GROQ_API_KEY_2");

        assertNotNull(p.nextKey());
    }
}
