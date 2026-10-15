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
// cluster: mã cụm (vd. 'do-dung') | undefined — F-A.
export async function fetchLessonWords(id, section, cluster) {
  const params = {};
  if (section) params.section = section;
  if (cluster) params.cluster = cluster;
  const { data } = await client.get(`/lessons/${id}/words`, { params });
  return data; // [{ id, lessonId, section, kana, kanji, romaji, meaningVi, pos, exampleJa, exampleVi, audioUrl, orderInLesson, studyStatus, cluster }]
}

// F-A: cụm chủ đề của một bài. section optional (VOCAB | REFERENCE | PHRASE).
// → [{ cluster, label, count }], sắp theo count giảm dần rồi cluster tăng dần.
// Từ chưa có cluster (null) gom vào 'khac'.
export async function fetchLessonClusters(id, section) {
  const { data } = await client.get(`/lessons/${id}/clusters`, {
    params: section ? { section } : {},
  });
  return data;
}
