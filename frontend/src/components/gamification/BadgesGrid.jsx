// Luoi 12 huy hieu (F-042) — render tu GET /api/gamification/badges.
// Icon = ky tu Nhat do backend dinh nghia; khong emoji.
function formatEarnedAt(value) {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleDateString('vi-VN');
}

export default function BadgesGrid({ badges }) {
  const list = Array.isArray(badges) ? badges : [];
  if (list.length === 0) {
    return <p className="muted">Chưa có huy hiệu nào để hiển thị.</p>;
  }
  return (
    <div className="ach-grid">
      {list.map((b) => (
        <div key={b.code} className={`ach-card${b.earned ? ' got' : ' locked'}`}>
          <span className={`jp-badge${b.earned ? ' gold' : ''}`}>{b.icon}</span>
          <p>{b.name}</p>
          <small>{b.description}</small>
          {b.earned && b.earnedAt && (
            <small className="ach-date">Đạt ngày {formatEarnedAt(b.earnedAt)}</small>
          )}
        </div>
      ))}
    </div>
  );
}
