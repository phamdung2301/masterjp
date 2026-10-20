import client from './client.js';

// Ngữ pháp (grammar) — dữ liệu theo docs/09-grammar-plan.md.
// Public, không cần đăng nhập.
// List view: [{ id, lessonNo, level, title, titleVi, pattern, meaningVi, tags }]
// Detail view: list view + explanation, formation, examples: [{ ja, kana, vi }], notes, compareWith: [id...]

export async function fetchGrammar({ lessonNo, level } = {}) {
  const params = {};
  if (lessonNo) params.lessonNo = lessonNo;
  if (level) params.level = level;
  const { data } = await client.get('/grammar', { params });
  return data;
}

export async function fetchGrammarDetail(id) {
  const { data } = await client.get(`/grammar/${encodeURIComponent(id)}`);
  return data;
}

export async function searchGrammar(q) {
  const { data } = await client.get('/grammar/search', { params: { q } });
  return data; // cùng shape list view
}
