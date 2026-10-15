import client from './client.js';

export async function fetchMySets() {
  const { data } = await client.get('/sets');
  return data;
}

export async function fetchSet(id) {
  const { data } = await client.get(`/sets/${id}`);
  return data;
}

export async function createSet({ name, description }) {
  const { data } = await client.post('/sets', { name, description });
  return data;
}

export async function updateSet(id, { name, description }) {
  const { data } = await client.patch(`/sets/${id}`, { name, description });
  return data;
}

export async function deleteSet(id) {
  await client.delete(`/sets/${id}`);
}

export async function addWordsToSet(id, wordIds) {
  const { data } = await client.post(`/sets/${id}/words`, { wordIds });
  return data; // { added, skipped }
}

export async function removeWordFromSet(id, wordId) {
  await client.delete(`/sets/${id}/words/${wordId}`);
}

export async function shareSet(id, isPublic) {
  const { data } = await client.patch(`/sets/${id}/share`, { isPublic });
  return data;
}

export async function fetchPublicSets(q) {
  const { data } = await client.get('/sets/public', { params: q ? { q } : {} });
  return data;
}

export async function forkSet(id) {
  const { data } = await client.post(`/sets/${id}/fork`);
  return data;
}
