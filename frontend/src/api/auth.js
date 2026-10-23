import client from './client.js';

// ⚠️ Response auth của backend có field `accessToken` — destructure ĐÚNG {accessToken}.
// Bug cũ: dùng {token} → localStorage lưu "undefined" → Bearer undefined → 401 toàn app.

export async function requestOtp(email, purpose) {
  await client.post('/auth/otp/request', { email, purpose });
}

export async function register({ email, code, password, name }) {
  const { data } = await client.post('/auth/register', { email, code, password, name });
  return data; // { accessToken, user }
}

export async function login(email, password) {
  const { data } = await client.post('/auth/login', { email, password });
  return data; // { accessToken, user }
}

export async function resetPassword({ email, code, newPassword }) {
  await client.post('/auth/password/reset', { email, code, newPassword });
}

export async function fetchMe() {
  const { data } = await client.get('/auth/me');
  return data; // { id, email, name, role, plan, planExpiresAt, streakDays }
}

// F-046 — toggle nhận email nhắc ôn tập (DOC-13 §5.6 field users.emailReminderOptIn).
// Contract (DOC-13 §9): PATCH /api/users/me, request { emailReminderOptIn: boolean },
// response { id, email, name, role, plan, planExpiresAt, streakDays, emailReminderOptIn }.
export async function updateMySettings(payload) {
  const { data } = await client.patch('/users/me', payload);
  return data;
}
