import client from './client.js';

// Sensei AI (F-038/039/040) — contract: ~/workspace/mjp-phase-b/docs/11-phase-b-sensei-ai.md
// Mọi endpoint dưới /api/ai/** yêu cầu JWT (interceptor trong client.js tự gắn).
// Request AI có thể chạy lâu (provider read timeout 30s phía backend) → timeout riêng 120s.
// Lưu ý: vite.config.js đã proxy '/api' → backend, KHÔNG cần thêm proxy entry.

const AI_TIMEOUT = 120000;

// F-038 — POST /api/ai/chat → { reply, usage: { provider } }
// message: 1–2000 ký tự sau trim. grammarPointId thắng lessonNo (BE cũng bỏ qua lessonNo khi có grammarPointId).
export async function chatSensei({ message, lessonNo, grammarPointId }) {
  const payload = { message: (message || '').trim() };
  if (grammarPointId) payload.grammarPointId = grammarPointId;
  else if (lessonNo != null) payload.lessonNo = lessonNo;
  const { data } = await client.post('/ai/chat', payload, { timeout: AI_TIMEOUT });
  return data;
}

// F-039 — POST /api/ai/grade-sentence → { score, corrected, errors: [{span, issue, fix}], explanation }
// sentence: 1–500 ký tự sau trim. grammarPointId bắt buộc.
export async function gradeSentence({ sentence, grammarPointId }) {
  const { data } = await client.post(
    '/ai/grade-sentence',
    { sentence: (sentence || '').trim(), grammarPointId },
    { timeout: AI_TIMEOUT },
  );
  return data;
}

// F-040 — POST /api/ai/generate-exercises → { exercises: [{ type, question, choices?, answer, explanation }] }
// count chỉ nhận 5 hoặc 10 (BE validate); B3 dùng 10.
export async function generateExercises({ grammarPointId, count = 10 }) {
  const { data } = await client.post(
    '/ai/generate-exercises',
    { grammarPointId, count },
    { timeout: AI_TIMEOUT },
  );
  return data;
}

// GET /api/ai/quota → { limit, used, remaining, resetAt } — KHÔNG tốn quota.
export async function fetchAiQuota() {
  const { data } = await client.get('/ai/quota');
  return data;
}

// Lỗi 429 DAILY_LIMIT: body { code, message, resetAt }.
export function isDailyLimit(error) {
  return error?.response?.status === 429 || error?.response?.data?.code === 'DAILY_LIMIT';
}

export function getDailyLimitResetAt(error) {
  const v = error?.response?.data?.resetAt;
  return typeof v === 'string' && v ? v : null;
}
