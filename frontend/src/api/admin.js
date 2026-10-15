import client from './client.js';

// Admin: bài học
export async function adminFetchLessons() {
  const { data } = await client.get('/admin/lessons');
  return data;
}
export async function adminCreateLesson(payload) {
  const { data } = await client.post('/admin/lessons', payload);
  return data;
}
export async function adminUpdateLesson(id, payload) {
  // ⚠️ BE chỉ có @PatchMapping — dùng PATCH, không dùng PUT (405).
  const { data } = await client.patch(`/admin/lessons/${id}`, payload);
  return data;
}
export async function adminDeleteLesson(id) {
  await client.delete(`/admin/lessons/${id}`);
}

// Admin: từ vựng
export async function adminFetchWords(params = {}) {
  const { data } = await client.get('/admin/words', { params });
  return data;
}
export async function adminCreateWord(payload) {
  const { data } = await client.post('/admin/words', payload);
  return data;
}
export async function adminUpdateWord(id, payload) {
  // ⚠️ BE chỉ có @PatchMapping — dùng PATCH, không dùng PUT (405).
  const { data } = await client.patch(`/admin/words/${id}`, payload);
  return data;
}
export async function adminDeleteWord(id) {
  await client.delete(`/admin/words/${id}`);
}

// Admin: import Excel — 2 bước validate → confirm.
// Contract (stateless, không importToken): validate trả { totalRows, validRows,
// errors: [{row,message}], rows: [...] } — FE giữ rows và gửi lại ở confirm.
export async function adminImportValidate(file) {
  const form = new FormData();
  form.append('file', file);
  const { data } = await client.post('/admin/words/import/validate', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data; // { totalRows, validRows, errors: [{ row, message }], rows: [{ lessonNo, section, kana, kanji, romaji, meaningVi, pos, exampleJa, exampleVi }] }
}
export async function adminImportConfirm(rows) {
  const { data } = await client.post('/admin/words/import/confirm', { rows });
  return data; // { imported, skipped }
}

// Admin: regenerate audio (job nền, poll 5s).
// Contract: POST -> { jobId, status }; GET job -> { jobId, status, total, done, failed }.
// status: QUEUED | RUNNING | DONE | FAILED.
export async function adminRegenerateAudio(wordIds) {
  const { data } = await client.post('/admin/audio/regenerate', { wordIds });
  return data; // { jobId, status }
}
export async function adminAudioJob(jobId) {
  const { data } = await client.get(`/admin/audio/jobs/${jobId}`);
  return data; // { jobId, status, total, done, failed }
}

// Admin: người dùng
export async function adminFetchUsers(q) {
  const { data } = await client.get('/admin/users', { params: q ? { q } : {} });
  return data;
}
export async function adminUpdateUserPlan(id, { plan, expiresAt }) {
  const { data } = await client.patch(`/admin/users/${id}/plan`, { plan, expiresAt });
  return data;
}

export async function adminFetchStats() {
  const { data } = await client.get('/admin/stats');
  return data;
}

export async function adminFetchAuditLogs(params = {}) {
  const { data } = await client.get('/admin/audit-logs', { params });
  return data;
}
