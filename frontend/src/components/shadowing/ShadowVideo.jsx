import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';

// ShadowVideo — F-041: trình phát video shadowing, trừu tượng hoá 2 nguồn:
//   video = { type: 'local', src: '/assets/shadowing/sh01.mp4' }
//         | { type: 'youtube', src: '<videoId>' }
// Component "câm": chỉ phát + báo sự kiện; loop / ghi âm / tính thời lượng xem
// do component cha (Shadowing.jsx) xử lý qua các callback tick.
// ref: { play(), pause(), seekTo(sec), setRate(r), getDuration() }
// Không dependency mới: YouTube dùng IFrame API nạp động, local dùng <video> native.
const ShadowVideo = forwardRef(function ShadowVideo(
  { video, onTick, onPlayState, onEnded, onMediaError, onReady },
  ref,
) {
  const videoRef = useRef(null);
  const ytHostRef = useRef(null);
  const ytPlayerRef = useRef(null);
  const modeRef = useRef(video?.type === 'youtube');
  modeRef.current = video?.type === 'youtube';

  // Callback luôn mới nhất — tránh stale closure trong event/poll của player.
  const cbRef = useRef(null);
  cbRef.current = { onTick, onPlayState, onEnded, onMediaError, onReady };

  useImperativeHandle(ref, () => ({
    play() {
      if (modeRef.current) {
        try { ytPlayerRef.current?.playVideo(); } catch { /* bỏ qua */ }
      } else {
        videoRef.current?.play().catch(() => {});
      }
    },
    pause() {
      if (modeRef.current) {
        try { ytPlayerRef.current?.pauseVideo(); } catch { /* bỏ qua */ }
      } else {
        videoRef.current?.pause();
      }
    },
    seekTo(sec) {
      const t = Math.max(0, Number(sec) || 0);
      if (modeRef.current) {
        try { ytPlayerRef.current?.seekTo(t, true); } catch { /* bỏ qua */ }
      } else if (videoRef.current) {
        videoRef.current.currentTime = t;
      }
    },
    setRate(r) {
      if (modeRef.current) {
        try {
          const p = ytPlayerRef.current;
          const avail = p?.getAvailablePlaybackRates?.() || [];
          if (p && avail.includes(r)) p.setPlaybackRate(r);
        } catch { /* bỏ qua */ }
      } else if (videoRef.current) {
        videoRef.current.playbackRate = r;
        try { videoRef.current.preservesPitch = true; } catch { /* không hỗ trợ */ }
      }
    },
    getDuration() {
      if (modeRef.current) {
        try { return ytPlayerRef.current?.getDuration() || 0; } catch { return 0; }
      }
      const d = videoRef.current?.duration;
      return Number.isFinite(d) ? d : 0;
    },
  }), []);

  // --- YouTube IFrame API (nạp 1 lần, singleton) ---
  useEffect(() => {
    if (video?.type !== 'youtube' || !video?.src) return;
    // L1 fix (security audit): defense-in-depth — videoId YouTube phai dung 11 ky tu
    // [A-Za-z0-9_-], ke ca khi JSON seed sau nay do user cung cap.
    if (!/^[A-Za-z0-9_-]{11}$/.test(video.src)) {
      cbRef.current.onMediaError?.('Mã video YouTube không hợp lệ.');
      return;
    }
    let cancelled = false;
    let pollId = null;
    const stopPoll = () => { if (pollId) { clearInterval(pollId); pollId = null; } };
    const startPoll = () => {
      stopPoll();
      pollId = setInterval(() => {
        try {
          const p = ytPlayerRef.current;
          if (!p?.getCurrentTime) return;
          cbRef.current.onTick?.(p.getCurrentTime(), p.getDuration() || 0);
        } catch { /* bỏ qua */ }
      }, 200);
    };

    loadYouTubeApi()
      .then((YT) => {
        if (cancelled || !ytHostRef.current) return;
        const player = new YT.Player(ytHostRef.current, {
          videoId: video.src,
          width: '100%',
          playerVars: { rel: 0, enablejsapi: 1, origin: window.location.origin },
          events: {
            onReady: (e) => {
              try { cbRef.current.onReady?.(e.target.getDuration?.() || 0); } catch { /* bỏ qua */ }
            },
            onStateChange: (e) => {
              const S = YT.PlayerState;
              const playing = e.data === S.PLAYING;
              cbRef.current.onPlayState?.(playing);
              if (playing) startPoll(); else stopPoll();
              if (e.data === S.ENDED) cbRef.current.onEnded?.();
            },
            onError: (e) => cbRef.current.onMediaError?.(ytErrorMessage(e?.data)),
          },
        });
        ytPlayerRef.current = player;
      })
      .catch(() => {
        if (!cancelled) cbRef.current.onMediaError?.('Không tải được trình phát YouTube. Kiểm tra kết nối mạng rồi thử lại.');
      });

    return () => {
      cancelled = true;
      stopPoll();
      try { ytPlayerRef.current?.destroy(); } catch { /* bỏ qua */ }
      ytPlayerRef.current = null;
    };
  }, [video?.type, video?.src]);

  if (video?.type === 'youtube') {
    // YouTube tự render chrome của nó vào div này.
    return <div ref={ytHostRef} className="shadow-yt" />;
  }

  return (
    <video
      ref={videoRef}
      className="shadow-video"
      src={video?.src}
      controls
      preload="metadata"
      playsInline
      onTimeUpdate={(e) => cbRef.current.onTick?.(e.target.currentTime, e.target.duration || 0)}
      onPlay={() => cbRef.current.onPlayState?.(true)}
      onPause={() => cbRef.current.onPlayState?.(false)}
      onEnded={() => cbRef.current.onEnded?.()}
      onDurationChange={(e) => cbRef.current.onReady?.(e.target.duration || 0)}
      onError={() => cbRef.current.onMediaError?.(
        'Không tải được video. Có thể video cho bài này chưa có — bạn vẫn có thể đọc phụ đề và dùng ghi âm để luyện nói.',
      )}
    />
  );
});

let ytApiPromise = null;
function loadYouTubeApi() {
  if (ytApiPromise) return ytApiPromise;
  ytApiPromise = new Promise((resolve, reject) => {
    if (window.YT?.Player) { resolve(window.YT); return; }
    // SHOULD-FIX #3 (code review): promise reject vinh vien neu giu cache —
    // reset de lan sau thu lai duoc khi mang hoi phuc.
    const timer = setTimeout(() => { ytApiPromise = null; reject(new Error('yt-timeout')); }, 15000);
    const prev = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => {
      clearTimeout(timer);
      if (typeof prev === 'function') { try { prev(); } catch { /* bỏ qua */ } }
      resolve(window.YT);
    };
    const tag = document.createElement('script');
    tag.src = 'https://www.youtube.com/iframe_api';
    tag.async = true;
    tag.onerror = () => { clearTimeout(timer); ytApiPromise = null; reject(new Error('yt-load')); };
    document.head.appendChild(tag);
  });
  return ytApiPromise;
}

function ytErrorMessage(code) {
  switch (code) {
    case 2: return 'Mã video YouTube không hợp lệ.';
    case 5: return 'YouTube đang trục trặc khi phát video này. Thử lại sau nhé.';
    case 100: return 'Video YouTube không tồn tại hoặc đã bị xóa.';
    case 101:
    case 150: return 'Video này không cho phép nhúng (embed) trên web khác.';
    default: return `Không phát được video YouTube (mã lỗi ${code ?? '?'}).`;
  }
}

export default ShadowVideo;
