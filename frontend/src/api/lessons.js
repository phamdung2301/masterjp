import client from './client.js';

export async function fetchLessons(level) {
  const { data } = await client.get('/lessons', { params: { level } });
  return data; // [{ id, level, lessonNo, titleJa, titleVi, order, totalWords, masteredWords }]
}

export async function fetchLesson(id) {
  const { data } = await client.get(`/lessons/${id}`);
  return data;
}

// section: 'VOCAB' | 'REFERENCE' | 'PHRASE' | undefined (tất cả)
export async function fetchLessonWords(id, section) {
  const { data } = await client.get(`/lessons/${id}/words`, {
    params: section ? { section } : {},
  });
  return data; // [{ id, lessonId, section, kana, kanji, romaji, meaningVi, pos, exampleJa, exampleVi, audioUrl, orderInLesson, studyStatus }]
}
