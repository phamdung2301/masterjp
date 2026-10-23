package com.masterjp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterjp.dto.AiDtos;
import com.masterjp.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase B — defensive parsing cho output JSON cua AI (F-039, F-040).
 * Pure static functions (khong phu thuoc HTTP/Mongo) de test-writer unit-test truc tiep.
 * <p>
 * Quy tac (spec DOC-11 4.3 / 5.1): parse loi hoac thieu field bat buoc / sai kieu
 * khong sua duoc -> 502 AI_UNAVAILABLE. Backend KHONG tu bia du lieu con thieu —
 * tha 502 con hon tra sai.
 */
public final class AiResponseParser {

    private AiResponseParser() {
    }

    /** Ket qua parse cua F-039 (dang noi bo; controller tra DTO tuong ung). */
    public record GradeData(int score, String corrected, List<AiDtos.ErrorItemDto> errors, String explanation) {
    }

    private static ApiException unavailable() {
        return new ApiException("AI_UNAVAILABLE",
                "Sensei AI đang bận, vui lòng thử lại sau.", HttpStatus.BAD_GATEWAY);
    }

    /**
     * Strip markdown code fence (```json ... ```) neu AI van boc, roi cat lay
     * doan JSON tu mo ngoac dau tien den dong ngoac cuoi cung (bo preamble/epilogue rac).
     * Ho tro ca object ({...}) va top-level array ([...]) — chon loai ngoac xuat hien truoc.
     */
    public static String stripToJson(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNl >= 0 && lastFence > firstNl) {
                s = s.substring(firstNl + 1, lastFence).trim();
            }
        }
        int beginBrace = s.indexOf('{');
        int beginBracket = s.indexOf('[');
        int begin;
        char close;
        if (beginBracket >= 0 && (beginBrace < 0 || beginBracket < beginBrace)) {
            begin = beginBracket;
            close = ']';
        } else {
            begin = beginBrace;
            close = '}';
        }
        int end = s.lastIndexOf(close);
        if (begin >= 0 && end > begin) {
            s = s.substring(begin, end + 1);
        }
        return s;
    }

    /** Text node khac blank, hoac null neu thieu / khong phai string / blank. */
    private static String textOrNull(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String t = node.asText();
        return t.isBlank() ? null : t;
    }

    private static JsonNode readRoot(String raw, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(stripToJson(raw));
            if (root == null || root.isMissingNode() || root.isNull()) {
                throw unavailable();
            }
            return root;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        }
    }

    /**
     * Parse output F-039. Validate: score la so -> int, clamp 0..100;
     * corrected/explanation string khong rong; errors la array (item thieu
     * field -> bo item do). Thieu score/corrected/explanation hoac sai kieu -> 502.
     */
    public static GradeData parseGrade(String raw, ObjectMapper mapper) {
        JsonNode root = readRoot(raw, mapper);
        if (!root.isObject()) {
            throw unavailable();
        }

        JsonNode scoreNode = root.get("score");
        if (scoreNode == null || !scoreNode.isNumber()) {
            throw unavailable();
        }
        int score = (int) Math.round(scoreNode.asDouble());
        score = Math.max(0, Math.min(100, score));

        String corrected = textOrNull(root.get("corrected"));
        String explanation = textOrNull(root.get("explanation"));
        if (corrected == null || explanation == null) {
            throw unavailable();
        }

        List<AiDtos.ErrorItemDto> errors = new ArrayList<>();
        JsonNode errorsNode = root.get("errors");
        if (errorsNode != null && errorsNode.isArray()) {
            for (JsonNode it : errorsNode) {
                String span = textOrNull(it.get("span"));
                String issue = textOrNull(it.get("issue"));
                String fix = textOrNull(it.get("fix"));
                if (span != null && issue != null && fix != null) {
                    errors.add(new AiDtos.ErrorItemDto(span, issue, fix));
                }
                // item thieu field -> bo qua (spec 4.3)
            }
        }

        return new GradeData(score, corrected, List.copyOf(errors), explanation);
    }

    /**
     * Parse output F-040. Validate:
     * - dung {@code expectedCount} exercises (length === count), sai -> 502;
     * - moi exercise: type "choice"|"fill"; question/answer/explanation string khong rong;
     * - choice: choices BẮT BUỘC — array dung 4 string phan biet, answer === mot trong 4;
     * - fill: choices vang mat hoac null; question chua placeholder "___".
     */
    public static List<AiDtos.ExerciseDto> parseExercises(String raw, int expectedCount, ObjectMapper mapper) {
        JsonNode root = readRoot(raw, mapper);

        // Chap nhan ca {"exercises": [...]} va top-level array (AI doi khi bo wrapper).
        JsonNode arr = root.isArray() ? root : root.get("exercises");
        if (arr == null || !arr.isArray() || arr.size() != expectedCount) {
            throw unavailable();
        }

        List<AiDtos.ExerciseDto> out = new ArrayList<>();
        for (JsonNode e : arr) {
            String type = textOrNull(e.get("type"));
            String question = textOrNull(e.get("question"));
            String answer = textOrNull(e.get("answer"));
            String explanation = textOrNull(e.get("explanation"));
            if (!("choice".equals(type) || "fill".equals(type))
                    || question == null || answer == null || explanation == null) {
                throw unavailable();
            }

            List<String> choices = null;
            if ("choice".equals(type)) {
                JsonNode c = e.get("choices");
                if (c == null || !c.isArray() || c.size() != 4) {
                    throw unavailable();
                }
                Set<String> seen = new HashSet<>();
                choices = new ArrayList<>();
                for (JsonNode x : c) {
                    String s = textOrNull(x);
                    if (s == null || !seen.add(s)) {
                        throw unavailable();
                    }
                    choices.add(s);
                }
                if (!choices.contains(answer)) {
                    throw unavailable();
                }
            } else {
                // type=fill: choices vang mat hoac null
                JsonNode c = e.get("choices");
                if (c != null && !c.isNull()) {
                    throw unavailable();
                }
                if (!question.contains("___")) {
                    throw unavailable();
                }
            }
            out.add(new AiDtos.ExerciseDto(type, question, choices, answer, explanation));
        }
        return List.copyOf(out);
    }
}
