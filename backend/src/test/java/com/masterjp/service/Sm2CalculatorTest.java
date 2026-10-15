package com.masterjp.service;

import com.masterjp.domain.ProgressStatus;
import com.masterjp.domain.WordProgress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test thuan cho SM-2 (khong can Spring/MongoDB).
 * Chay bang: mvn test
 */
class Sm2CalculatorTest {

    private WordProgress fresh() {
        WordProgress p = new WordProgress();
        p.setUserId("u1");
        p.setWordId("w1");
        return p;
    }

    @Test
    void lanDauDung_repetitions1_interval1_learning() {
        WordProgress p = fresh();
        Sm2Calculator.apply(p, true);

        assertEquals(1, p.getRepetitions());
        assertEquals(1, p.getIntervalDays());
        assertEquals(ProgressStatus.LEARNING, p.getStatus());
        assertEquals(2.5, p.getEaseFactor());
        assertEquals(1, p.getCorrectCount());
        assertNotNull(p.getNextReviewAt());
    }

    @Test
    void lanThuHaiDung_interval6() {
        WordProgress p = fresh();
        Sm2Calculator.apply(p, true);
        Sm2Calculator.apply(p, true);

        assertEquals(2, p.getRepetitions());
        assertEquals(6, p.getIntervalDays());
        assertEquals(ProgressStatus.LEARNING, p.getStatus());
    }

    @Test
    void lanThuBaDung_intervalNhanEaseFactor() {
        WordProgress p = fresh();
        Sm2Calculator.apply(p, true); // interval=1
        Sm2Calculator.apply(p, true); // interval=6
        Sm2Calculator.apply(p, true); // interval=round(6*2.5)=15

        assertEquals(3, p.getRepetitions());
        assertEquals(15, p.getIntervalDays());
        assertEquals(ProgressStatus.LEARNING, p.getStatus());
    }

    @Test
    void dung4LanLienTiep_intervalDu30Ngay_thanhMastered() {
        WordProgress p = fresh();
        Sm2Calculator.apply(p, true); // 1
        Sm2Calculator.apply(p, true); // 6
        Sm2Calculator.apply(p, true); // 15
        Sm2Calculator.apply(p, true); // round(15*2.5)=38 >= 30

        assertEquals(4, p.getRepetitions());
        assertTrue(p.getIntervalDays() >= 30);
        assertEquals(ProgressStatus.MASTERED, p.getStatus());
    }

    @Test
    void traLoiSai_resetRepetitions_interval1_truEaseFactor() {
        WordProgress p = fresh();
        Sm2Calculator.apply(p, true);
        Sm2Calculator.apply(p, true);
        Sm2Calculator.apply(p, false);

        assertEquals(0, p.getRepetitions());
        assertEquals(1, p.getIntervalDays());
        assertEquals(1, p.getLapses());
        assertEquals(2.3, p.getEaseFactor(), 0.0001);
        assertEquals(1, p.getWrongCount());
        assertEquals(ProgressStatus.LEARNING, p.getStatus());
    }

    @Test
    void easeFactorKhongXuongDuoi1_3() {
        WordProgress p = fresh();
        for (int i = 0; i < 20; i++) {
            Sm2Calculator.apply(p, false);
        }
        assertTrue(p.getEaseFactor() >= 1.3);
        assertEquals(1.3, p.getEaseFactor(), 0.0001);
    }

    @Test
    void saiNgayLanDau_statusLearning() {
        WordProgress p = fresh();
        Sm2Calculator.apply(p, false);

        assertEquals(0, p.getRepetitions());
        assertEquals(1, p.getIntervalDays());
        assertEquals(ProgressStatus.LEARNING, p.getStatus());
        assertNotNull(p.getNextReviewAt());
    }
}
