package com.masterjp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterjp.dto.AiDtos;
import com.masterjp.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test cho AiResponseParser (Phase B — Sensei AI).
 * Pure static functions: khong can Spring/MongoDB, khong goi network.
 * Chay bang: mvn test -Dtest=AiResponseParserTest
 */
class AiResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- stripToJson ----------

    @Test
    void stripToJson_goBoMarkdownCodeFence() {
        String raw = "```json\n{\"score\": 90}\n```";
        assertEquals("{\"score\": 90}", AiResponseParser.stripToJson(raw));
    }

    @Test
    void stripToJson_goBoPreambleVaEpilogueRac() {
        String raw = "Đây là kết quả chấm:\n{\"score\": 90, \"a\": 1}\nChúc bạn học tốt!";
        assertEquals("{\"score\": 90, \"a\": 1}", AiResponseParser.stripToJson(raw));
    }

    @Test
    void stripToJson_nullThanhChuoiRong() {
        assertEquals("", AiResponseParser.stripToJson(null));
    }

    // ---------- parseGrade ----------

    private String gradeJson(double score) {
        return "{\"score\": " + score + ", "
                + "\"corrected\": \"わたしは まいにち べんきょうします。\", "
                + "\"errors\": ["
                + "{\"span\": \"べんきょします\", \"issue\": \"Sai chia thể ます\", \"fix\": \"べんきょうします\"}, "
                + "{\"span\": \"thiếu issue và fix\"}"
                + "], "
                + "\"explanation\": \"Câu đúng ngữ pháp cơ bản.\"}";
    }

    @Test
    void parseGrade_happyPath_scoreLamTron_itemThieuFieldBiBo() {
        AiResponseParser.GradeData d = AiResponseParser.parseGrade(gradeJson(85.4), mapper);

        assertEquals(85, d.score());
        assertEquals("わたしは まいにち べんきょうします。", d.corrected());
        assertEquals(1, d.errors().size()); // item thieu field bi bo, khong fail ca response
        AiDtos.ErrorItemDto e = d.errors().get(0);
        assertEquals("べんきょします", e.span());
        assertEquals("Sai chia thể ます", e.issue());
        assertEquals("べんきょうします", e.fix());
        assertEquals("Câu đúng ngữ pháp cơ bản.", d.explanation());
    }

    @Test
    void parseGrade_chapNhanCodeFence() {
        String raw = "```json\n" + gradeJson(70) + "\n```";
        AiResponseParser.GradeData d = AiResponseParser.parseGrade(raw, mapper);
        assertEquals(70, d.score());
    }

    @Test
    void parseGrade_scoreVuot100BiClampVe100() {
        assertEquals(100, AiResponseParser.parseGrade(gradeJson(150), mapper).score());
    }

    @Test
    void parseGrade_scoreAmBiClampVe0() {
        assertEquals(0, AiResponseParser.parseGrade(gradeJson(-20), mapper).score());
    }

    @Test
    void parseGrade_errorsKhongPhaiArray_thiBoQuaKhongFail() {
        String raw = "{\"score\": 80, \"corrected\": \"ok\", \"errors\": \"khong-phai-array\", "
                + "\"explanation\": \"giai thich\"}";
        AiResponseParser.GradeData d = AiResponseParser.parseGrade(raw, mapper);
        assertTrue(d.errors().isEmpty());
    }

    private void assertUnavailable(Runnable r) {
        ApiException e = assertThrows(ApiException.class, r::run);
        assertEquals("AI_UNAVAILABLE", e.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
    }

    @Test
    void parseGrade_jsonRac_502() {
        assertUnavailable(() -> AiResponseParser.parseGrade("xin chào, tôi không phải JSON", mapper));
    }

    @Test
    void parseGrade_thieuScore_502() {
        assertUnavailable(() -> AiResponseParser.parseGrade(
                "{\"corrected\": \"ok\", \"explanation\": \"giai thich\"}", mapper));
    }

    @Test
    void parseGrade_scoreKhongPhaiSo_502() {
        assertUnavailable(() -> AiResponseParser.parseGrade(
                "{\"score\": \"cao\", \"corrected\": \"ok\", \"explanation\": \"giai thich\"}", mapper));
    }

    @Test
    void parseGrade_thieuCorrected_502() {
        assertUnavailable(() -> AiResponseParser.parseGrade(
                "{\"score\": 80, \"explanation\": \"giai thich\"}", mapper));
    }

    @Test
    void parseGrade_thieuExplanation_502() {
        assertUnavailable(() -> AiResponseParser.parseGrade(
                "{\"score\": 80, \"corrected\": \"ok\"}", mapper));
    }

    @Test
    void parseGrade_rootKhongPhaiObject_502() {
        assertUnavailable(() -> AiResponseParser.parseGrade("[1, 2, 3]", mapper));
    }

    // ---------- parseExercises ----------

    private String choiceEx(String answer) {
        return "{\"type\": \"choice\", "
                + "\"question\": \"ここに名前を ___ ください。(Hãy viết tên vào đây)\", "
                + "\"choices\": [\"かきます\", \"かきません\", \"かきました\", \"かきたい\"], "
                + "\"answer\": \"" + answer + "\", "
                + "\"explanation\": \"Động từ thể て + ください diễn tả yêu cầu.\"}";
    }

    private String fillEx() {
        return "{\"type\": \"fill\", "
                + "\"question\": \"まいにち ___ べんきょうします。(Mỗi ngày tôi đều học)\", "
                + "\"answer\": \"にほんごを\", "
                + "\"explanation\": \"を đánh dấu tân ngữ trực tiếp.\"}";
    }

    private String wrapperJson(String... exercises) {
        return "{\"exercises\": [" + String.join(", ", exercises) + "]}";
    }

    @Test
    void parseExercises_happyPath_tronChoiceVaFill() {
        List<AiDtos.ExerciseDto> out =
                AiResponseParser.parseExercises(wrapperJson(choiceEx("かきます"), fillEx()), 2, mapper);

        assertEquals(2, out.size());
        AiDtos.ExerciseDto c = out.get(0);
        assertEquals("choice", c.type());
        assertEquals(4, c.choices().size());
        assertEquals("かきます", c.answer());
        AiDtos.ExerciseDto f = out.get(1);
        assertEquals("fill", f.type());
        assertNull(f.choices()); // fill: choices phai null
        assertTrue(f.question().contains("___"));
    }

    @Test
    void parseExercises_chapNhanTopLevelArray_khongCanWrapper() {
        String raw = "[" + choiceEx("かきます") + "]";
        List<AiDtos.ExerciseDto> out = AiResponseParser.parseExercises(raw, 1, mapper);
        assertEquals(1, out.size());
        assertNotNull(out.get(0));
    }

    @Test
    void parseExercises_saiCount_502() {
        assertUnavailable(() -> AiResponseParser.parseExercises(wrapperJson(choiceEx("かきます")), 5, mapper));
    }

    @Test
    void parseExercises_choiceChiCo3DapAn_502() {
        String ex = "{\"type\": \"choice\", \"question\": \"q ___\", "
                + "\"choices\": [\"a\", \"b\", \"c\"], \"answer\": \"a\", \"explanation\": \"e\"}";
        assertUnavailable(() -> AiResponseParser.parseExercises(wrapperJson(ex), 1, mapper));
    }

    @Test
    void parseExercises_choiceTrungDapAn_502() {
        String ex = "{\"type\": \"choice\", \"question\": \"q ___\", "
                + "\"choices\": [\"a\", \"a\", \"b\", \"c\"], \"answer\": \"a\", \"explanation\": \"e\"}";
        assertUnavailable(() -> AiResponseParser.parseExercises(wrapperJson(ex), 1, mapper));
    }

    @Test
    void parseExercises_choiceThieuChoices_502() {
        String ex = "{\"type\": \"choice\", \"question\": \"q ___\", "
                + "\"answer\": \"a\", \"explanation\": \"e\"}";
        assertUnavailable(() -> AiResponseParser.parseExercises(wrapperJson(ex), 1, mapper));
    }

    @Test
    void parseExercises_choiceAnswerNgoaiChoices_502() {
        assertUnavailable(() -> AiResponseParser.parseExercises(
                wrapperJson(choiceEx("ない答え")), 1, mapper));
    }

    @Test
    void parseExercises_fillCoChoices_502() {
        String ex = "{\"type\": \"fill\", \"question\": \"q ___\", "
                + "\"choices\": [\"a\", \"b\"], \"answer\": \"a\", \"explanation\": \"e\"}";
        assertUnavailable(() -> AiResponseParser.parseExercises(wrapperJson(ex), 1, mapper));
    }

    @Test
    void parseExercises_fillThieuPlaceholder_502() {
        String ex = "{\"type\": \"fill\", \"question\": \"câu không có chỗ trống\", "
                + "\"answer\": \"a\", \"explanation\": \"e\"}";
        assertUnavailable(() -> AiResponseParser.parseExercises(wrapperJson(ex), 1, mapper));
    }

    @Test
    void parseExercises_typeLa_502() {
        String ex = "{\"type\": \"matching\", \"question\": \"q ___\", "
                + "\"answer\": \"a\", \"explanation\": \"e\"}";
        assertUnavailable(() -> AiResponseParser.parseExercises(wrapperJson(ex), 1, mapper));
    }

    @Test
    void parseExercises_thieuQuestion_502() {
        String ex = "{\"type\": \"fill\", \"answer\": \"a\", \"explanation\": \"e\"}";
        assertUnavailable(() -> AiResponseParser.parseExercises(wrapperJson(ex), 1, mapper));
    }
}
