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

// Kanji topics: 17 chủ đề + số chữ thực tế trong DB.
// → [{ code, label, count }]. Public, không cần đăng nhập.
export async function fetchKanjiTopics() {
  const { data } = await client.get('/kanji/topics');
  return data;
}

// F-D (R3): tạo quiz kanji — cần đăng nhập.
// mode: 'KANJI_MEANING' | 'MEANING_KANJI' | 'KANJI_TYPING'.
// → đúng shape QuizResponse như quiz thường: { questions: [...] }.
// KANJI_TYPING: options rỗng, `kana` = chuỗi nối mọi on/kun để FE chấm local.
// v1 KHÔNG lưu SM-2 / progress (backend chỉ sinh câu hỏi).
export async function generateKanjiQuiz({ characters, mode }) {
  const { data } = await client.post('/kanji/quiz', { characters, mode });
  return data;
}
