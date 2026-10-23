import client from './client.js';

export async function searchWords(q) {
  const { data } = await client.get('/words/search', { params: { q } });
  return data;
}

// mode: 'JP_VI' | 'VI_JP' | 'LISTENING' | 'TYPING'
// TYPING (F-037): response questions có thêm kana/kanji/meaningVi, options rỗng;
// FE chấm local bằng wanakana, chỉ gửi correct lên server.
export async function generateQuiz({ mode, scopeType, scopeId, limit = 20 }) {
  const { data } = await client.post('/quiz/generate', { mode, scopeType, scopeId, limit });
  return data; // { questions: [{ wordId, promptText, promptAudioUrl, options: [{ wordId, text }], answerWordId }] }
}

export async function answerProgress({ kind, wordId, character, correct, timeMs }) {
  // F-043: kind "vocab" (mặc định, cần wordId) | "kanji" (cần character — 1 ký tự).
  const { data } = await client.patch('/progress/answer', { kind, wordId, character, correct, timeMs });
  return data; // { status, nextReviewAt, ... }
}

// F-043 (review queue thống nhất): hàng đợi trộn wrong + vocab đến hạn + kanji đến hạn.
export async function fetchReviewQueue(limit = 50) {
  const { data } = await client.get('/review/queue', { params: { limit } });
  return data; // { items: [{ kind, ... }], counts: { vocab, kanji, wrong, total }, estimatedMinutes }
}

// F-043: resolve 1 câu sai sau khi trả lời đúng trong phiên ôn tập.
export async function resolveWrong(id) {
  const { data } = await client.patch(`/review/wrong/${id}`, { resolved: true });
  return data; // { id, resolved: true }
}

export async function fetchDueWords(limit = 50) {
  const { data } = await client.get('/progress/due', { params: { limit } });
  return data;
}

export async function fetchProgressSummary() {
  const { data } = await client.get('/progress/summary');
  return data; // { byLevel, lessons, streakDays, recentSessions }
}

export async function recordSession({ mode, scopeType, scopeId, totalItems, correctCount, items, startedAt, finishedAt }) {
  const { data } = await client.post('/sessions', {
    mode, scopeType, scopeId, totalItems, correctCount, items, startedAt, finishedAt,
  });
  // F-042: huy hieu moi dat trong phien nay -> ban event cho NewBadgeToast (neu duoc mount o App root).
  const newBadges = Array.isArray(data?.newBadges) ? data.newBadges : [];
  if (newBadges.length > 0 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('mjp:new-badges', { detail: { codes: newBadges } }));
  }
  return data; // { id, streakDays, newBadges }
}
