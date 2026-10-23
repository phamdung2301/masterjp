// Gamification API (F-042): huy hieu, heatmap, bang xep hang tuan, profile.
import client from './client.js';

/** GET /api/gamification/badges — ca 12 huy hieu + trang thai da dat. */
export async function fetchBadges() {
  const { data } = await client.get('/gamification/badges');
  return data; // { badges: [{ code, icon, name, description, earned, earnedAt }], earnedCount, totalCount }
}

/** GET /api/gamification/heatmap?weeks=12 — weeks 1..52. */
export async function fetchHeatmap(weeks = 12) {
  const { data } = await client.get('/gamification/heatmap', { params: { weeks } });
  return data; // { weeks, days: [{ date: 'yyyy-MM-dd', minutes, sessions }] }
}

/** GET /api/gamification/leaderboard?week=YYYY-Www — week bo qua => tuan hien tai. */
export async function fetchLeaderboard(week) {
  const { data } = await client.get('/gamification/leaderboard', {
    params: week ? { week } : {},
  });
  return data; // { week, weekStart, weekEnd, entries: [{ rank, nickname, xp, sessions, isMe }], myRank, totalParticipants }
}

/**
 * PATCH /api/gamification/profile — doi nickname (1-20 ky tu) / opt-in.
 * Ca 2 field tuy chon; PATCH voi {} khong doi gi, chi tra cai dat hien tai.
 */
export async function updateGamificationProfile({ nickname, leaderboardOptIn } = {}) {
  const { data } = await client.patch('/gamification/profile', { nickname, leaderboardOptIn });
  return data; // { nickname, leaderboardOptIn }
}
