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

export async function answerProgress({ wordId, correct, timeMs }) {
  const { data } = await client.patch('/progress/answer', { wordId, correct, timeMs });
  return data; // { status, nextReviewAt, ... }
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
  return data; // { id, streakDays }
}
