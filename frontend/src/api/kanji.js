import client from './client.js';

// F-B: kanji. Contract docs/06-feature-pack-2-backend-contract.md — field case-sensitive.
// Public, không cần đăng nhập.
export async function fetchKanji({ lessonNo, jlpt } = {}) {
  const params = {};
  if (lessonNo) params.lessonNo = lessonNo;
  if (jlpt) params.jlpt = jlpt;
  const { data } = await client.get('/kanji', { params });
  return data; // [{ character, onYomi, kunYomi, meaningVi, strokes, jlpt, lessonNos }]
}

export async function fetchKanjiDetail(character) {
  const { data } = await client.get(`/kanji/${encodeURIComponent(character)}`);
  return data; // KanjiDto + words (tối đa 50 từ, studyStatus luôn null)
}
