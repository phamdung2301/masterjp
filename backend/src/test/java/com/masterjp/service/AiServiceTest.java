package com.masterjp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterjp.domain.AiGrade;
import com.masterjp.domain.Grammar;
import com.masterjp.dto.AiDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.AiGradeRepository;
import com.masterjp.repository.GrammarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho AiService (Phase B — Sensei AI).
 * Dung constructor package-private de inject HttpClient mock — KHONG goi network that.
 * GroqKeyProvider dung instance that voi MockEnvironment de kiem tra rotation/fallback.
 * Chay bang: mvn test -Dtest=AiServiceTest
 */
@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiRateLimiter rateLimiter;
    @Mock
    private GrammarRepository grammarRepository;
    @Mock
    private GrammarService grammarService;
    @Mock
    private AiGradeRepository aiGradeRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private WrongAnswerService wrongAnswerService;
    @Mock
    private HttpClient httpClient;

    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- helpers ----------

    private GroqKeyProvider keyProvider(String... pairs) {
        MockEnvironment env = new MockEnvironment();
        for (int i = 0; i < pairs.length; i += 2) {
            env.setProperty(pairs[i], pairs[i + 1]);
        }
        return new GroqKeyProvider(env);
    }

    private AiService service(GroqKeyProvider kp, MockEnvironment env) {
        return new AiService(kp, rateLimiter, grammarRepository, grammarService,
                aiGradeRepository, mongoTemplate, mapper, env, wrongAnswerService, httpClient);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> httpResponse(int status, String body) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(body);
        return r;
    }

    /** Body response OpenAI-compatible: {"choices": [{"message": {"content": ...}}]}. */
    private String chatCompletion(String innerContent) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", innerContent)))));
    }

    /** Mock HttpClient.send tra ve cac response theo thu tu goi. */
    private void stubSend(HttpResponse<String> first, HttpResponse<String>... rest) throws Exception {
        when(httpClient.<String>send(any(HttpRequest.class), any()))
                .thenReturn(first, rest);
    }

    private void stubSendThrow(IOException e) throws Exception {
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenThrow(e);
    }

    private void stubQuota() {
        when(rateLimiter.consume("u1")).thenReturn(
                new AiDtos.QuotaResponse(50, 1, 49, "2026-10-23T00:00:00+07:00"));
    }

    private Grammar grammar() {
        Grammar g = new Grammar();
        g.setId("g1");
        g.setTitle("～てください");
        g.setPattern("V-て + ください");
        g.setMeaningVi("hãy làm gì (yêu cầu)");
        g.setLessonNo(4);
        return g;
    }

    private void assertUnavailable(ApiException e) {
        assertEquals("AI_UNAVAILABLE", e.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
    }

    // ---------- F-038 chat: validation ----------

    @Test
    void chat_cauHoiRong_400() {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());

        ApiException e = assertThrows(ApiException.class,
                () -> s.chat("u1", new AiDtos.ChatRequest("  ", null, null)));

        assertEquals("VALIDATION_ERROR", e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void chat_cauHoiQua2000KyTu_400() {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        String tooLong = "あ".repeat(2001);

        ApiException e = assertThrows(ApiException.class,
                () -> s.chat("u1", new AiDtos.ChatRequest(tooLong, null, null)));

        assertEquals("VALIDATION_ERROR", e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    // ---------- F-038 chat: provider plumbing ----------

    @Test
    void chat_happyPath_groqTraLoi_providerGroq() throws Exception {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        stubQuota();
        stubSend(httpResponse(200, chatCompletion("  Xin chào!  ")));

        AiDtos.ChatResponse resp = s.chat("u1", new AiDtos.ChatRequest("こんにちは", null, null));

        assertEquals("Xin chào!", resp.reply()); // reply duoc trim
        assertEquals("groq", resp.usage().provider());
        verify(rateLimiter).consume("u1"); // quota tru TRUOC khi goi provider
    }

    @Test
    void chat_voiGrammarPointId_injectContextTuRepo() throws Exception {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        when(grammarRepository.findByIdAndActiveTrue("g1")).thenReturn(Optional.of(grammar()));
        stubQuota();
        stubSend(httpResponse(200, chatCompletion("ok")));

        s.chat("u1", new AiDtos.ChatRequest("giải thích", null, "g1"));

        verify(grammarRepository).findByIdAndActiveTrue("g1");
    }

    @Test
    void chat_groq401_danhDauKeyBad_vaThuKeyTiepTheo() throws Exception {
        GroqKeyProvider kp = keyProvider("GROQ_API_KEY", "k1", "GROQ_API_KEY_2", "k2");
        AiService s = service(kp, new MockEnvironment());
        stubQuota();
        stubSend(httpResponse(401, "unauthorized"),
                httpResponse(200, chatCompletion("trả lời từ key 2")));

        AiDtos.ChatResponse resp = s.chat("u1", new AiDtos.ChatRequest("hello", null, null));

        assertEquals("trả lời từ key 2", resp.reply());
        assertEquals("groq", resp.usage().provider());
        assertTrue(kp.isBad("GROQ_API_KEY")); // key bi 401 phai bi danh dau bad
    }

    @Test
    void chat_loiNetworkRetryable_thuKeyTiepTheo() throws Exception {
        GroqKeyProvider kp = keyProvider("GROQ_API_KEY", "k1", "GROQ_API_KEY_2", "k2");
        AiService s = service(kp, new MockEnvironment());
        stubQuota();
        // key-1 timeout (retryable) -> key-2 thanh cong
        when(httpClient.<String>send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("timeout"))
                .thenReturn(httpResponse(200, chatCompletion("ok sau retry")));

        AiDtos.ChatResponse resp = s.chat("u1", new AiDtos.ChatRequest("hello", null, null));

        assertEquals("ok sau retry", resp.reply());
    }

    @Test
    void chat_groq400_502NgayKhongRetryKhongFallback() throws Exception {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        stubQuota();
        stubSend(httpResponse(400, "bad request"));

        ApiException e = assertThrows(ApiException.class,
                () -> s.chat("u1", new AiDtos.ChatRequest("hello", null, null)));

        assertUnavailable(e);
        verify(httpClient, times(1)).<String>send(any(HttpRequest.class), any());
    }

    @Test
    void chat_hetGroqKeys_fallbackSangOpenRouter() throws Exception {
        GroqKeyProvider kp = keyProvider("GROQ_API_KEY", "k1", "GROQ_API_KEY_2", "k2");
        MockEnvironment env = new MockEnvironment();
        env.setProperty("OPENROUTER_API_KEY", "or-key");
        AiService s = service(kp, env);
        stubQuota();
        stubSend(httpResponse(401, "unauthorized"),
                httpResponse(401, "unauthorized"),
                httpResponse(200, chatCompletion("trả lời từ OpenRouter")));

        AiDtos.ChatResponse resp = s.chat("u1", new AiDtos.ChatRequest("hello", null, null));

        assertEquals("trả lời từ OpenRouter", resp.reply());
        assertEquals("openrouter", resp.usage().provider());
    }

    @Test
    void chat_tatCaProviderThatBai_502() throws Exception {
        GroqKeyProvider kp = keyProvider("GROQ_API_KEY", "k1", "GROQ_API_KEY_2", "k2");
        AiService s = service(kp, new MockEnvironment()); // khong co OPENROUTER_API_KEY
        stubQuota();
        stubSendThrow(new IOException("timeout"));

        ApiException e = assertThrows(ApiException.class,
                () -> s.chat("u1", new AiDtos.ChatRequest("hello", null, null)));

        assertUnavailable(e);
    }

    // ---------- F-039 gradeSentence ----------

    @Test
    void gradeSentence_cauRong_400() {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());

        ApiException e = assertThrows(ApiException.class,
                () -> s.gradeSentence("u1", new AiDtos.GradeSentenceRequest("", "g1")));

        assertEquals("VALIDATION_ERROR", e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void gradeSentence_grammarPointKhongTonTai_400() {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        when(grammarRepository.findByIdAndActiveTrue("khong-co")).thenReturn(Optional.empty());

        ApiException e = assertThrows(ApiException.class,
                () -> s.gradeSentence("u1", new AiDtos.GradeSentenceRequest("わたしは学生です。", "khong-co")));

        assertEquals("VALIDATION_ERROR", e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void gradeSentence_happyPath_luuAiGrade_vaTraKetQua() throws Exception {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        when(grammarRepository.findByIdAndActiveTrue("g1")).thenReturn(Optional.of(grammar()));
        stubQuota();
        String gradeJson = "{\"score\": 85, "
                + "\"corrected\": \"わたしは まいにち べんきょうします。\", "
                + "\"errors\": [{\"span\": \"べんきょします\", \"issue\": \"Sai chia thể\", \"fix\": \"べんきょうします\"}], "
                + "\"explanation\": \"Câu đúng ngữ pháp.\"}";
        stubSend(httpResponse(200, chatCompletion(gradeJson)));

        AiDtos.GradeSentenceResponse resp =
                s.gradeSentence("u1", new AiDtos.GradeSentenceRequest("わたしは まいにち べんきょします。", "g1"));

        assertEquals(85, resp.score());
        assertEquals("わたしは まいにち べんきょうします。", resp.corrected());
        assertEquals(1, resp.errors().size());
        assertEquals("Câu đúng ngữ pháp.", resp.explanation());

        // persist vao ai_grades chi khi grading thanh cong
        ArgumentCaptor<AiGrade> saved = ArgumentCaptor.forClass(AiGrade.class);
        verify(aiGradeRepository).save(saved.capture());
        assertEquals("u1", saved.getValue().getUserId());
        assertEquals("g1", saved.getValue().getGrammarPointId());
        assertEquals(85, saved.getValue().getScore());
        assertEquals("groq", saved.getValue().getProvider());
        assertEquals(1, saved.getValue().getErrors().size());
    }

    @Test
    void gradeSentence_providerTraJsonRac_502_khongLuuGrade() throws Exception {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        when(grammarRepository.findByIdAndActiveTrue("g1")).thenReturn(Optional.of(grammar()));
        stubQuota();
        stubSend(httpResponse(200, chatCompletion("không phải JSON")));

        ApiException e = assertThrows(ApiException.class,
                () -> s.gradeSentence("u1", new AiDtos.GradeSentenceRequest("わたしは学生です。", "g1")));

        assertUnavailable(e);
        verify(aiGradeRepository, never()).save(any(AiGrade.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gradeSentence_vuot200BanGhi_xoaBanGhiCuNhat() throws Exception {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        when(grammarRepository.findByIdAndActiveTrue("g1")).thenReturn(Optional.of(grammar()));
        stubQuota();
        String gradeJson = "{\"score\": 90, \"corrected\": \"ok。\", "
                + "\"errors\": [], \"explanation\": \"Tốt.\"}";
        stubSend(httpResponse(200, chatCompletion(gradeJson)));

        // user da co 205 ban ghi -> overflow 5 -> phai xoa 5 ban ghi cu nhat
        when(aiGradeRepository.countByUserId("u1")).thenReturn(205L);
        List<AiGrade> oldest = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AiGrade g = new AiGrade();
            g.setId("old-" + i);
            oldest.add(g);
        }
        when(mongoTemplate.find(any(Query.class), eq(AiGrade.class)))
                .thenReturn(oldest);

        AiDtos.GradeSentenceResponse resp =
                s.gradeSentence("u1", new AiDtos.GradeSentenceRequest("わたしは学生です。", "g1"));

        assertEquals(90, resp.score());
        // ban ghi moi van duoc luu truoc khi xoa cu
        verify(aiGradeRepository).save(any(AiGrade.class));
        ArgumentCaptor<List<AiGrade>> deleted = ArgumentCaptor.forClass(List.class);
        verify(aiGradeRepository).deleteAll(deleted.capture());
        assertEquals(5, deleted.getValue().size());
        assertEquals("old-0", deleted.getValue().get(0).getId());
    }

    // ---------- F-040 generateExercises ----------

    @Test
    void generateExercises_countKhongHopLe_400() {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());

        ApiException e = assertThrows(ApiException.class,
                () -> s.generateExercises("u1", new AiDtos.GenerateExercisesRequest("g1", 7)));

        assertEquals("VALIDATION_ERROR", e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void generateExercises_grammarPointKhongTonTai_400() {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        when(grammarRepository.findByIdAndActiveTrue("khong-co")).thenReturn(Optional.empty());

        ApiException e = assertThrows(ApiException.class,
                () -> s.generateExercises("u1", new AiDtos.GenerateExercisesRequest("khong-co", 5)));

        assertEquals("VALIDATION_ERROR", e.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void generateExercises_happyPath_traDung5Bai() throws Exception {
        AiService s = service(keyProvider("GROQ_API_KEY", "k1"), new MockEnvironment());
        when(grammarRepository.findByIdAndActiveTrue("g1")).thenReturn(Optional.of(grammar()));
        stubQuota();
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                arr.append(", ");
            }
            arr.append("{\"type\": \"choice\", \"question\": \"câu ").append(i).append(" ___\", "
                    + "\"choices\": [\"a").append(i).append("\", \"b").append(i)
                    .append("\", \"c").append(i).append("\", \"d").append(i).append("\"], "
                    + "\"answer\": \"a").append(i).append("\", \"explanation\": \"giải thích ").append(i).append("\"}");
        }
        arr.append("]");
        stubSend(httpResponse(200, chatCompletion("{\"exercises\": " + arr + "}")));

        AiDtos.GenerateExercisesResponse resp =
                s.generateExercises("u1", new AiDtos.GenerateExercisesRequest("g1", 5));

        assertEquals(5, resp.exercises().size());
        assertEquals("choice", resp.exercises().get(0).type());
    }
}
