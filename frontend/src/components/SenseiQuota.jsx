import { useCallback, useEffect, useState } from 'react';
import { fetchAiQuota } from '../api/ai.js';
import './sensei.css';

// Quota Sensei AI — GET /api/ai/quota (không tốn quota).
// available=false khi endpoint chưa tồn tại (404 — backend chưa deploy) → UI ẩn chỉ báo.
export function useAiQuota() {
  const [quota, setQuota] = useState(null); // { limit, used, remaining, resetAt }
  const [loading, setLoading] = useState(true);
  const [available, setAvailable] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const q = await fetchAiQuota();
      if (q && typeof q === 'object') {
        setQuota(q);
        setAvailable(true);
      }
    } catch (e) {
      // Backend chưa deploy endpoint quota → ẩn chỉ báo, không báo lỗi.
      if (e?.response?.status === 404) setAvailable(false);
    } finally {
      setLoading(false);
    }
  }, []);

  return { quota, loading, available, refresh };
}

// Chỉ báo nhỏ: "Sensei AI: còn X lượt hôm nay".
// refreshKey: component cha tăng sau mỗi lần gọi AI để số lượt cập nhật ngay.
export default function SenseiQuota({ refreshKey = 0, className = '' }) {
  const { quota, loading, available, refresh } = useAiQuota();

  useEffect(() => {
    refresh();
  }, [refresh, refreshKey]);

  if (!available) return null;
  if (loading && !quota) return null;
  const remaining = Number(quota?.remaining);
  if (!Number.isFinite(remaining)) return null;
  if (remaining <= 0) {
    return (
      <span className={`sensei-quota empty ${className}`.trim()}>
        Sensei AI: hết lượt hôm nay · quota mới vào lúc 00:00
      </span>
    );
  }
  return (
    <span className={`sensei-quota ${className}`.trim()}>
      Sensei AI: còn {remaining} lượt hôm nay
    </span>
  );
}
