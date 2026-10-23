import { useEffect, useRef, useState } from 'react';
import { recordSession } from '../api/study.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import LoginPrompt from '../components/LoginPrompt.jsx';
import Confetti from '../components/Confetti.jsx';
import ShadowVideo from '../components/shadowing/ShadowVideo.jsx';
import {
  CheckIcon, DownloadIcon, FlameIcon, MicIcon, PlayIcon, PauseIcon, RepeatIcon,
} from '../components/icons.jsx';
import '../components/shadowing/shadowing.css';

// Shadowing — F-041: luyện nói theo video hội thoại có phụ đề song ngữ.
// Sống trong Lesson Hub (tab "Shadowing", không route riêng — DOC-12 §3.6).
// Nội dung = static JSON (public/data/shadowing/), backend surface = 0 endpoint mới;
// hoàn thành phiên → POST /api/sessions hiện có với mode=SHADOWING.
// Bản ghi âm local-only (memory của tab, không upload).
const SPEEDS = [0.75, 1, 1.25];
const COMPLETE_RATIO = 0.8;

function fmt(sec) {
  const s = Math.max(0, Math.floor(sec || 0));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

function pickAudioMime() {
  if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') return '';
  return ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'].find((t) => {
    try { return MediaRecorder.isTypeSupported(t); } catch { return false; }
  }) || '';
}

export default function Shadowing({ videoId, lessonId, durationSec = 0 }) {
  const { isLoggedIn } = useAuth();

  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  const [showJa, setShowJa] = useState(true);
  const [showVi, setShowVi] = useState(true);
  const [selLine, setSelLine] = useState(null); // câu đang chọn (loop / ghi âm)
  const [rate, setRate] = useState(1);
  const [playing, setPlaying] = useState(false);
  const [curTime, setCurTime] = useState(0);
  const [duration, setDuration] = useState(durationSec);
  const [watched, setWatched] = useState(0);
  const [loopOne, setLoopOne] = useState(false);
  const [loopAB, setLoopAB] = useState(null); // {a, b}
  const [mediaError, setMediaError] = useState('');

  const [recording, setRecording] = useState(false);
  const [recSec, setRecSec] = useState(0);
  const [recUrl, setRecUrl] = useState('');
  const [recMime, setRecMime] = useState('');
  const [micError, setMicError] = useState('');

  const [posting, setPosting] = useState(false);
  const [postError, setPostError] = useState('');
  const [result, setResult] = useState(null);
  const [loginMsg, setLoginMsg] = useState('');

  const playerRef = useRef(null);
  const listRef = useRef(null);
  const recRef = useRef(null); // { mr, stream }
  const recUrlRef = useRef('');
  const startedAtRef = useRef('');
  const lastTickRef = useRef(0);
  const watchedRef = useRef(0);

  const lines = detail?.lines || [];
  const activeIdx = lines.findIndex((l) => curTime >= l.start && curTime < l.end);
  const target = duration > 0 ? duration : 0;
  const watchedPct = target > 0 ? Math.min(100, Math.round((watched / target) * 100)) : 0;
  const eligible = !result && !mediaError && target > 0 && watched >= target * COMPLETE_RATIO;

  // --- Tải nội dung shadowing (static JSON) ---
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError('');
    setDetail(null);
    fetch(`${import.meta.env.BASE_URL}data/shadowing/${encodeURIComponent(videoId)}.json`)
      .then((r) => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
      .then((d) => {
        if (!alive) return;
        if (!d || !d.video || !Array.isArray(d.lines) || d.lines.length === 0) throw new Error('bad-shape');
        setDetail(d);
      })
      .catch(() => { if (alive) setError('Không tải được dữ liệu shadowing. Kiểm tra kết nối rồi thử lại.'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [videoId, retry]);

  // Dọn recorder khi unmount (không để mic chạy nền).
  useEffect(() => () => {
    const r = recRef.current;
    try { if (r?.mr && r.mr.state !== 'inactive') r.mr.stop(); } catch { /* bỏ qua */ }
    try { r?.stream?.getTracks().forEach((tr) => tr.stop()); } catch { /* bỏ qua */ }
    if (recUrlRef.current) URL.revokeObjectURL(recUrlRef.current);
  }, []);

  // Đồng hồ đếm giờ ghi âm.
  useEffect(() => {
    if (!recording) return;
    const t0 = Date.now();
    const id = setInterval(() => setRecSec(Math.floor((Date.now() - t0) / 1000)), 500);
    return () => clearInterval(id);
  }, [recording]);

  // Cuộn dòng phụ đề đang phát vào tầm nhìn.
  useEffect(() => {
    listRef.current?.querySelector('.shadow-line.active')?.scrollIntoView({ block: 'nearest' });
  }, [activeIdx]);

  // --- Player callbacks (ShadowVideo giữ bản mới nhất qua ref — không lo stale) ---
  const onTick = (t, dur) => {
    setCurTime(t);
    const dt = t - lastTickRef.current;
    lastTickRef.current = t;
    if (playing && dt > 0 && dt < 1.5) {
      watchedRef.current += dt;
      setWatched(watchedRef.current);
    }
    if (recording && selLine != null) {
      const L = lines[selLine];
      if (L && t >= L.end) { stopRecording(); return; }
    } else if (loopOne && selLine != null) {
      const L = lines[selLine];
      if (L && t >= L.end - 0.03) playerRef.current?.seekTo(L.start);
    } else if (loopAB) {
      const endB = lines[loopAB.b]?.end;
      if (endB != null && t >= endB) playerRef.current?.seekTo(lines[loopAB.a].start);
    }
    if (dur > 0 && dur !== duration) setDuration(dur);
  };

  const onPlayState = (isPlaying) => {
    setPlaying(isPlaying);
    if (isPlaying && !startedAtRef.current) startedAtRef.current = new Date().toISOString();
  };

  const onReady = (d) => {
    if (d > 0) setDuration(d);
    playerRef.current?.setRate(rate);
  };

  // --- Điều khiển phát lại ---
  const selectLine = (i) => {
    setSelLine(i);
    const L = lines[i];
    if (!L || mediaError) return; // không có video: vẫn chọn câu để đọc/ghi âm
    playerRef.current?.seekTo(L.start + 0.01);
    playerRef.current?.play();
  };

  const togglePlay = () => {
    if (mediaError) return;
    if (playing) playerRef.current?.pause();
    else playerRef.current?.play();
  };

  const changeRate = (r) => {
    setRate(r);
    playerRef.current?.setRate(r);
  };

  const toggleLoopOne = () => {
    if (loopOne) { setLoopOne(false); return; }
    let i = selLine;
    if (i == null) { i = activeIdx >= 0 ? activeIdx : 0; setSelLine(i); }
    setLoopAB(null);
    setLoopOne(true);
  };

  const markA = () => {
    const i = selLine ?? (activeIdx >= 0 ? activeIdx : null);
    if (i == null) return;
    setLoopOne(false);
    setLoopAB((prev) => ({ a: i, b: prev && prev.b > i ? prev.b : i }));
  };

  const markB = () => {
    const i = selLine ?? (activeIdx >= 0 ? activeIdx : null);
    if (i == null) return;
    setLoopOne(false);
    setLoopAB((prev) => ({ a: prev && prev.a < i ? prev.a : i, b: i }));
  };

  const playOriginal = () => {
    const i = selLine ?? (activeIdx >= 0 ? activeIdx : 0);
    if (i == null || mediaError) return;
    playerRef.current?.seekTo(lines[i].start + 0.01);
    playerRef.current?.play();
  };

  // --- Ghi âm (MediaRecorder, local-only) ---
  const startRecording = async () => {
    if (typeof MediaRecorder === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
      setMicError('Trình duyệt này không hỗ trợ ghi âm. Hãy dùng Chrome hoặc Edge bản mới nhất.');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mime = pickAudioMime();
      const mr = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
      const chunks = [];
      mr.ondataavailable = (e) => { if (e.data && e.data.size > 0) chunks.push(e.data); };
      mr.onstop = () => {
        const blob = new Blob(chunks, { type: mr.mimeType || 'audio/webm' });
        if (recUrlRef.current) URL.revokeObjectURL(recUrlRef.current);
        const url = URL.createObjectURL(blob);
        recUrlRef.current = url;
        setRecUrl(url);
        setRecMime(mr.mimeType || 'audio/webm');
        try { stream.getTracks().forEach((tr) => tr.stop()); } catch { /* bỏ qua */ }
      };
      recRef.current = { mr, stream };
      mr.start();
      setRecSec(0);
      setRecording(true);
      setMicError('');
      setRecUrl('');
      const i = selLine ?? (activeIdx >= 0 ? activeIdx : 0);
      if (i != null && lines[i] && !mediaError) {
        setSelLine(i);
        playerRef.current?.seekTo(lines[i].start + 0.01);
        playerRef.current?.play();
      }
    } catch {
      setMicError('Không truy cập được microphone. Bấm biểu tượng ổ khóa trên thanh địa chỉ để cấp quyền, rồi thử lại. Các tính năng xem video và phụ đề vẫn dùng bình thường.');
    }
  };

  const stopRecording = () => {
    const r = recRef.current;
    try { if (r?.mr && r.mr.state !== 'inactive') r.mr.stop(); } catch { /* bỏ qua */ }
    playerRef.current?.pause();
    setRecording(false);
  };

  // --- Hoàn thành phiên → POST /api/sessions hiện có (mode=SHADOWING) ---
  const completeSession = async () => {
    if (!isLoggedIn) {
      setLoginMsg('Đăng nhập để lưu phiên shadowing vào tiến trình và chuỗi ngày học của bạn.');
      return;
    }
    if (!eligible || posting) return;
    setPosting(true);
    setPostError('');
    try {
      const res = await recordSession({
        mode: 'SHADOWING',
        scopeType: 'LESSON',
        scopeId: lessonId,
        totalItems: 1,
        correctCount: 1,
        items: [{ wordId: `shadow:${videoId}`, correct: true, timeMs: Math.round(watchedRef.current * 1000) }],
        startedAt: startedAtRef.current || new Date().toISOString(),
        finishedAt: new Date().toISOString(),
      });
      setResult({ streakDays: res?.streakDays ?? null });
    } catch (e) {
      setPostError(getErrorMessage(e, 'Không lưu được phiên học. Thử lại sau.'));
    } finally {
      setPosting(false);
    }
  };

  if (loading) return <div><p className="muted">Đang tải dữ liệu shadowing…</p></div>;
  if (error) {
    return (
      <div>
        <div className="alert alert-error">{error}</div>
        <button type="button" className="btn btn-outline btn-sm" onClick={() => setRetry((r) => r + 1)}>
          Thử lại
        </button>
      </div>
    );
  }
  if (!detail) return null;

  const recExt = /mp4/i.test(recMime) ? 'm4a' : 'webm';

  return (
    <div className="shadow">
      <div className="shadow-head">
        <span className="jp-badge shu">影</span>
        <div>
          <h2>{detail.title} <span className="muted">— {detail.titleVi}</span></h2>
          <p className="muted" style={{ margin: '4px 0 0' }}>
            Nghe từng câu, bấm vào câu để nhảy tới đúng chỗ, lặp lại và ghi âm giọng mình để so sánh với bản gốc.
          </p>
        </div>
      </div>

      <div className="shadow-grid">
        <div className="shadow-player-col">
          {mediaError ? (
            <div className="shadow-novideo" role="status">
              <span className="jp-badge gold">映</span>
              <h3>Sắp có video</h3>
              <p className="muted">
                Video hội thoại cho bài này đang được chuẩn bị. Bạn vẫn có thể đọc phụ đề
                song ngữ bên dưới và dùng tính năng ghi âm để luyện nói.
              </p>
            </div>
          ) : (
            <ShadowVideo
              ref={playerRef}
              video={detail.video}
              onTick={onTick}
              onPlayState={onPlayState}
              onEnded={() => {}}
              onMediaError={(msg) => { setMediaError(msg); setPlaying(false); }}
              onReady={onReady}
            />
          )}

          {!mediaError && (
            <div className="shadow-controls" role="toolbar" aria-label="Điều khiển phát lại">
              <button type="button" className="btn btn-outline btn-sm" onClick={togglePlay}
                aria-label={playing ? 'Tạm dừng' : 'Phát'}>
                {playing ? <PauseIcon size={16} /> : <PlayIcon size={16} />}
              </button>
              <div className="shadow-speed" role="group" aria-label="Tốc độ phát">
                {SPEEDS.map((s) => (
                  <button key={s} type="button"
                    className={`shadow-speed-btn${rate === s ? ' active' : ''}`}
                    onClick={() => changeRate(s)} aria-pressed={rate === s}>
                    {String(s).replace('.', ',')}x
                  </button>
                ))}
              </div>
              <button type="button" className={`btn btn-sm${loopOne ? ' btn-primary' : ' btn-outline'}`}
                onClick={toggleLoopOne} aria-pressed={loopOne} title="Lặp lại câu đang chọn">
                <RepeatIcon size={16} /> Lặp câu
              </button>
              <div className="shadow-ab" role="group" aria-label="Lặp đoạn A–B">
                <button type="button" className="btn btn-outline btn-sm" onClick={markA}
                  title="Đánh dấu điểm bắt đầu đoạn lặp (câu đang chọn)">
                  A{loopAB ? `: câu ${loopAB.a + 1}` : ''}
                </button>
                <button type="button" className="btn btn-outline btn-sm" onClick={markB}
                  title="Đánh dấu điểm kết thúc đoạn lặp (câu đang chọn)">
                  B{loopAB ? `: câu ${loopAB.b + 1}` : ''}
                </button>
                {loopAB && (
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => setLoopAB(null)}>
                    Hủy
                  </button>
                )}
              </div>
            </div>
          )}

          <div className="shadow-rec" aria-live="polite">
            <h3><MicIcon size={18} /> Ghi âm giọng mình</h3>
            <p className="muted" style={{ margin: '0 0 10px' }}>
              Bấm ghi âm — video sẽ phát từ câu đang chọn trong khi bạn đọc theo. Bản ghi chỉ lưu
              trên máy này, không tải lên server.
            </p>
            {micError && <div className="alert alert-error">{micError}</div>}
            {!recording && !recUrl && (
              <button type="button" className="btn btn-outline" onClick={startRecording}>
                <MicIcon size={16} /> Bắt đầu ghi âm
              </button>
            )}
            {recording && (
              <div className="shadow-rec-live">
                <span className="rec-dot" aria-hidden="true" />
                <span>Đang ghi… {fmt(recSec)}</span>
                <button type="button" className="btn btn-primary btn-sm" onClick={stopRecording}>
                  Dừng
                </button>
              </div>
            )}
            {!recording && recUrl && (
              <div className="shadow-rec-done">
                <div className="shadow-rec-plays">
                  <button type="button" className="btn btn-outline btn-sm" onClick={playOriginal}
                    disabled={!!mediaError} title="Phát lại câu gốc từ video">
                    <PlayIcon size={16} /> Nghe gốc
                  </button>
                  <span className="shadow-rec-label">Nghe mình:</span>
                </div>
                <audio className="shadow-audio" src={recUrl} controls />
                <div className="shadow-rec-actions">
                  <a className="btn btn-outline btn-sm" href={recUrl} download={`shadowing-${videoId}.${recExt}`}>
                    <DownloadIcon size={16} /> Tải bản ghi về máy
                  </a>
                  <button type="button" className="btn btn-ghost btn-sm" onClick={startRecording}>
                    Ghi lại
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        <div className="shadow-sub-col">
          <div className="shadow-toggles" role="group" aria-label="Hiện/ẩn phụ đề">
            <button type="button" className={`btn btn-sm${showJa ? ' btn-primary' : ' btn-outline'}`}
              onClick={() => setShowJa((v) => !v)} aria-pressed={showJa}>
              Tiếng Nhật
            </button>
            <button type="button" className={`btn btn-sm${showVi ? ' btn-primary' : ' btn-outline'}`}
              onClick={() => setShowVi((v) => !v)} aria-pressed={showVi}>
              Tiếng Việt
            </button>
            <span className="muted">{lines.length} câu</span>
          </div>
          <div className="shadow-subs" ref={listRef} role="list" aria-label="Phụ đề song ngữ">
            {lines.map((l, i) => (
              <button
                key={i}
                type="button"
                role="listitem"
                className={`shadow-line${i === activeIdx ? ' active' : ''}${i === selLine ? ' selected' : ''}`}
                onClick={() => selectLine(i)}
                title={`Nhảy tới ${fmt(l.start)}`}
              >
                <span className="shadow-line-num">{i + 1}</span>
                <span className="shadow-line-text">
                  {showJa && (
                    <span className="shadow-ja">
                      {l.ja}
                      {l.kana && l.kana !== l.ja && <span className="shadow-kana">{l.kana}</span>}
                    </span>
                  )}
                  {showVi && <span className="shadow-vi">{l.vi}</span>}
                  {!showJa && !showVi && <span className="shadow-vi muted">Đã ẩn cả hai ngôn ngữ</span>}
                </span>
                <span className="shadow-line-time">{fmt(l.start)}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="shadow-complete">
        {result ? (
          <>
            <Confetti run />
            <div className="shadow-done" role="status">
              <CheckIcon size={22} />
              <div>
                <b>Đã lưu phiên shadowing!</b>
                {result.streakDays != null && (
                  <div className="streak-line"><FlameIcon size={16} /> Chuỗi ngày học: {result.streakDays} ngày</div>
                )}
                <div className="muted">Phiên này đã được tính vào tiến trình và huy hiệu của bạn.</div>
              </div>
            </div>
          </>
        ) : (
          <>
            <div className="shadow-watched">
              <div className="shadow-watched-bar" aria-hidden="true"><i style={{ width: `${watchedPct}%` }} /></div>
              <span className="muted">
                Đã xem {fmt(watched)} / {fmt(target)}
                {mediaError
                  ? ' — chưa thể hoàn thành phiên học vì video chưa có.'
                  : ` — xem đủ ${Math.round(COMPLETE_RATIO * 100)}% để hoàn thành (${fmt(target * COMPLETE_RATIO)}).`}
              </span>
            </div>
            {postError && <div className="alert alert-error">{postError}</div>}
            <button
              type="button"
              className="btn btn-primary"
              disabled={!eligible || posting}
              onClick={completeSession}
              title={eligible ? 'Lưu phiên shadowing' : 'Xem đủ 80% thời lượng video để hoàn thành'}
            >
              {posting ? 'Đang lưu…' : 'Hoàn thành phiên'}
            </button>
            {!isLoggedIn && (
              <p className="muted" style={{ margin: '8px 0 0' }}>
                Đăng nhập để lưu phiên vào tiến trình và chuỗi ngày học của bạn.
              </p>
            )}
          </>
        )}
      </div>

      <LoginPrompt open={!!loginMsg} onClose={() => setLoginMsg('')} message={loginMsg} />
    </div>
  );
}
