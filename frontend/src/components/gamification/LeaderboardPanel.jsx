// Bang xep hang tuan (F-042) — opt-in only, an danh (chi nickname + isMe).
// Tu quan ly state: tuan hien tai / truoc / sau, toggle opt-in, doi nickname.
import { useCallback, useEffect, useState } from 'react';
import { fetchLeaderboard, updateGamificationProfile } from '../../api/gamification.js';
import { getErrorMessage } from '../../api/client.js';

function isoWeeksInYear(y) {
  const jan1 = new Date(Date.UTC(y, 0, 1)).getUTCDay(); // 0=CN
  const leap = (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
  return jan1 === 4 || (jan1 === 3 && leap) ? 53 : 52;
}

// '2026-W43' -> '43 · 2026' cho hien thi tieng Viet.
function fmtWeek(weekStr) {
  const m = /^(\d{4})-W(\d{2})$/.exec(weekStr || '');
  if (!m) return weekStr;
  return `${Number(m[2])} · ${m[1]}`;
}

function shiftIsoWeek(weekStr, delta) {
  const m = /^(\d{4})-W(\d{2})$/.exec(weekStr || '');
  if (!m) return weekStr;
  let y = Number(m[1]);
  let w = Number(m[2]) + delta;
  while (w < 1) {
    y -= 1;
    w += isoWeeksInYear(y);
  }
  while (w > isoWeeksInYear(y)) {
    w -= isoWeeksInYear(y);
    y += 1;
  }
  return `${y}-W${String(w).padStart(2, '0')}`;
}

export default function LeaderboardPanel() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [profile, setProfile] = useState(null);
  const [formNickname, setFormNickname] = useState('');
  const [formOptIn, setFormOptIn] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveMsg, setSaveMsg] = useState('');

  const load = useCallback((wk) => {
    setLoading(true);
    setError('');
    fetchLeaderboard(wk)
      .then(setData)
      .catch((e) => setError(getErrorMessage(e, 'Không tải được bảng xếp hạng.')))
      .finally(() => setLoading(false));
  }, []);

  const loadProfile = useCallback(() => {
    // PATCH voi body rong: khong doi gi, chi doc cai dat hien tai.
    updateGamificationProfile({})
      .then((p) => {
        setProfile(p);
        setFormNickname(p.nickname || '');
        setFormOptIn(!!p.leaderboardOptIn);
      })
      .catch(() => {
        // loi thi giu form mac dinh, nguoi dung van xem duoc bang
      });
  }, []);

  useEffect(() => {
    load(undefined);
    loadProfile();
  }, [load, loadProfile]);

  const goWeek = (delta) => {
    if (!data?.week) return;
    load(shiftIsoWeek(data.week, delta));
  };

  const handleSave = async (e) => {
    e.preventDefault();
    const nick = formNickname.trim();
    if (nick.length < 1 || nick.length > 20) {
      setSaveMsg('Biệt danh phải từ 1 đến 20 ký tự.');
      return;
    }
    setSaving(true);
    setSaveMsg('');
    try {
      const p = await updateGamificationProfile({ nickname: nick, leaderboardOptIn: formOptIn });
      setProfile(p);
      setFormNickname(p.nickname || '');
      setSaveMsg('Đã lưu cài đặt.');
      load(data?.week); // opt-in/out co hieu luc ngay tren bang
    } catch (err) {
      setSaveMsg(getErrorMessage(err, 'Không lưu được cài đặt.'));
    } finally {
      setSaving(false);
    }
  };

  const entries = Array.isArray(data?.entries) ? data.entries : [];

  return (
    <section className="leaderboard-wrap" aria-label="Bảng xếp hạng tuần">
      <h3 style={{ marginBottom: 2 }}>Bảng xếp hạng tuần</h3>
      <p className="muted" style={{ marginTop: 0 }}>
        Top 50 theo XP tuần · chỉ người đã bật tham gia mới xuất hiện · chỉ hiển thị biệt danh
      </p>

      {loading && <p className="muted">Đang tải bảng xếp hạng…</p>}
      {error && <div className="alert alert-error">{error}</div>}

      {!loading && !error && data && (
        <>
          <div className="lb-nav">
            <button type="button" className="btn btn-sm btn-outline" onClick={() => goWeek(-1)}>
              ← Tuần trước
            </button>
            <strong>Tuần {fmtWeek(data.week)}</strong>
            <button type="button" className="btn btn-sm btn-outline" onClick={() => goWeek(1)}>
              Tuần sau →
            </button>
          </div>

          {data.myRank != null && (
            <p className="lb-myrank">
              Bạn đang xếp hạng <strong>{data.myRank}</strong>/{data.totalParticipants}
            </p>
          )}
          {data.myRank == null && profile?.leaderboardOptIn && (
            <p className="muted">Tuần này bạn chưa học phiên nào — ôn một lượt để góp mặt trên bảng nhé!</p>
          )}

          {entries.length === 0 ? (
            <p className="muted">Tuần này chưa có ai tham gia bảng xếp hạng.</p>
          ) : (
            <div className="lb-table-wrap">
              <table className="lb-table">
                <thead>
                  <tr>
                    <th>Hạng</th>
                    <th>Biệt danh</th>
                    <th>XP</th>
                    <th>Phiên</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((en) => (
                    <tr key={en.rank} className={en.isMe ? 'me' : ''}>
                      <td>{en.rank}</td>
                      <td>
                        {en.nickname}
                        {en.isMe && ' (bạn)'}
                      </td>
                      <td>{en.xp}</td>
                      <td>{en.sessions}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            {data.totalParticipants} người tham gia · XP = số câu đúng × 10 + tổng số câu × 2
          </p>
        </>
      )}

      <form className="lb-profile" onSubmit={handleSave}>
        <h4>Cài đặt bảng xếp hạng</h4>
        <label className="lb-optin">
          <input
            type="checkbox"
            checked={formOptIn}
            onChange={(e) => setFormOptIn(e.target.checked)}
          />
          Hiển thị tôi trên bảng xếp hạng tuần
        </label>
        <div className="lb-nick-row">
          <input
            value={formNickname}
            onChange={(e) => setFormNickname(e.target.value)}
            maxLength={20}
            placeholder="Biệt danh của bạn"
            aria-label="Biệt danh"
          />
          <button type="submit" className="btn btn-sm" disabled={saving}>
            {saving ? 'Đang lưu…' : 'Lưu'}
          </button>
        </div>
        <small className="muted">Biệt danh hiển thị công khai — đừng dùng tên thật hay email.</small>
        {saveMsg && <p className="muted" style={{ marginBottom: 0 }}>{saveMsg}</p>}
      </form>
    </section>
  );
}
