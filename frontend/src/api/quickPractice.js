import client from './client.js';

// F-C: luyện tập nhanh. Contract docs/06-feature-pack-2-backend-contract.md.
// Cần đăng nhập (401 nếu chưa login). KHÔNG tạo CustomSet, KHÔNG tạo StudySession.
export async function generateQuickQuiz({ wordIds, mode }) {
  const { data } = await client.post('/quiz/quick', { wordIds, mode });
  return data; // đúng shape QuizResponse như quiz thường: { questions: [...] }
}
