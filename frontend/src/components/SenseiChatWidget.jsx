import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { chatSensei, isDailyLimit } from '../api/ai.js';
import { getErrorMessage } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import { ChatIcon, CrossIcon, SendIcon } from './icons.jsx';
import SenseiQuota from './SenseiQuota.jsx';
import './sensei.css';

// F-038 — Sensei AI chat: floating button + panel, mount 1 lần trong App (mọi trang đều thấy).
// Bus mở widget từ bất kỳ đâu, kèm ngữ cảnh điểm ngữ pháp:
//   openSenseiChat({ grammarPointId, title, lessonNo })  — VD: nút "Hỏi Sensei AI" trên GrammarPointCard.
// Hội thoại giữ trong memory (mất khi reload) theo persistence contract F-038.

const listeners = new Set();
export function openSenseiChat(ctx = {}) {
  listeners.forEach((fn) => {
    try { fn(ctx); } catch { /* listener lỗi không được sập caller */ }
  });
}

const MAX_LEN = 2000;
const SUGGESTIONS = [
  'ています và てあります khác nhau thế nào?',
  'Cách phân biệt trợ từ は và が?',
  'Thể ない của động từ nhóm 1 chia thế nào?',
];

function escapeHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function inlineMd(s) {
  return escapeHtml(s)
    .replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>');
}

// Render markdown đơn giản, KHÔNG thêm dependency.
// Toàn bộ nội dung AI được escape HTML trước — chỉ các thẻ do hàm này
// tự sinh mới xuất hiện trong DOM (không đưa output thô vào innerHTML).
function renderMarkdown(src) {
  const parts = [];
  const fenceRe = /```(?:\w+)?\n?([\s\S]*?)```/g;
  let last = 0;
  let m;
  const text = String(src || '');
  while ((m = fenceRe.exec(text)) !== null) {
    parts.push({ type: 'md', text: text.slice(last, m.index) });
    parts.push({ type: 'code', text: m[1] });
    last = m.index + m[0].length;
  }
  parts.push({ type: 'md', text: text.slice(last) });
  return parts.map((p) => (p.type === 'code'
    ? `<pre class="md-pre"><code>${escapeHtml(p.text.trim())}</code></pre>`
    : mdBlocks(p.text))).join('');
}

function mdBlocks(text) {
  const lines = String(text).split('\n');
  let html = '';
  let listOpen = false;
  const closeList = () => { if (listOpen) { html += '</ul>'; listOpen = false; } };
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) { closeList(); continue; }
    let mm;
    if ((mm = line.match(/^#{1,4}\s+(.*)$/))) {
      closeList();
      html += `<div class="md-h">${inlineMd(mm[1])}</div>`;
    } else if ((mm = line.match(/^[-*•]\s+(.*)$/))) {
      if (!listOpen) { html += '<ul class="md-list">'; listOpen = true; }
      html += `<li>${inlineMd(mm[1])}</li>`;
    } else if ((mm = line.match(/^\d+[.)]\s+(.*)$/))) {
      if (!listOpen) { html += '<ul class="md-list">'; listOpen = true; }
      html += `<li>${inlineMd(mm[1])}</li>`;
    } else {
      closeList();
      html += `<p class="md-p">${inlineMd(line)}</p>`;
    }
  }
  closeList();
  return html;
}

let msgSeq = 0;
const nextId = () => `sensei-msg-${++msgSeq}`;

function MessageBubble({ msg, onRetry }) {
  if (msg.role === 'user') {
    // Text của user render qua React (tự escape) — không dùng innerHTML.
    return <div className="sensei-msg user">{msg.text}</div>;
  }
  if (msg.role === 'error') {
    return (
      <div className="sensei-msg error">
        <span>{msg.text}</span>
        {msg.retryable && msg.payload && (
          <button type="button" className="btn btn-outline btn-sm" onClick={onRetry}>
            Thử lại
          </button>
        )}
      </div>
    );
  }
  return (
    <div className="sensei-msg ai">
      <div dangerouslySetInnerHTML={{ __html: renderMarkdown(msg.text) }} />
    </div>
  );
}

export default function SenseiChatWidget() {
  const { isLoggedIn } = useAuth();
  const [open, setOpen] = useState(false);
  const [ctx, setCtx] = useState(null); // { grammarPointId, title, lessonNo } | null
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [aiCalls, setAiCalls] = useState(0);
  const bodyRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    const handler = (c) => {
      setCtx(c && c.grammarPointId ? { grammarPointId: c.grammarPointId, title: c.title || '', lessonNo: c.lessonNo ?? null } : null);
      setOpen(true);
    };
    listeners.add(handler);
    return () => { listeners.delete(handler); };
  }, []);

  useEffect(() => {
    if (open) {
      const el = bodyRef.current;
      if (el) el.scrollTop = el.scrollHeight;
      inputRef.current?.focus();
    }
  }, [open, messages]);

  const pushMsg = (msg) => {
    const id = nextId(); // tinh id NGOAI updater — updater phai pure (StrictMode co the goi 2 lan)
    setMessages((prev) => [...prev, { id, ...msg }]);
  };

  const send = async (rawText) => {
    const message = (rawText ?? input).trim();
    if (!message || sending) return;
    if (message.length > MAX_LEN) {
      pushMsg({ role: 'error', text: `Câu hỏi quá dài (tối đa ${MAX_LEN} ký tự). Hãy rút gọn lại nhé.` });
      return;
    }
    const payload = { message };
    if (ctx?.grammarPointId) payload.grammarPointId = ctx.grammarPointId;
    if (ctx?.lessonNo != null) payload.lessonNo = ctx.lessonNo;
    pushMsg({ role: 'user', text: message });
    setInput('');
    setSending(true);
    try {
      const data = await chatSensei(payload);
      setAiCalls((c) => c + 1);
      const reply = typeof data?.reply === 'string' && data.reply.trim() ? data.reply : null;
      if (!reply) throw new Error('Sensei AI trả về câu trả lời trống, hãy thử hỏi lại.');
      pushMsg({ role: 'ai', text: reply });
    } catch (e) {
      // F-045: hết quota AI (429 DAILY_LIMIT) → interceptor đã mở PaywallModal;
      // bubble trong chat chỉ báo ngắn gọn, không lặp lại message thô.
      const quotaOut = isDailyLimit(e);
      pushMsg({
        role: 'error',
        text: quotaOut
          ? 'Hết lượt hỏi Sensei AI hôm nay rồi — Tanu-chan vừa mở bảng nâng cấp, bạn xem thử nhé!'
          : getErrorMessage(e, 'Sensei AI đang bận, vui lòng thử lại sau.'),
        retryable: !quotaOut,
        payload,
      });
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <button
        type="button"
        className="sensei-fab"
        onClick={() => setOpen((o) => !o)}
        aria-label={open ? 'Đóng chat Sensei AI' : 'Mở chat Sensei AI'}
        title="Hỏi Sensei AI"
      >
        {open ? <CrossIcon size={22} /> : <ChatIcon size={22} />}
      </button>

      {open && (
        <div className="sensei-panel" role="dialog" aria-label="Chat với Sensei AI">
          <div className="sensei-head">
            <div>
              <div className="sensei-title">Sensei AI</div>
              <div className="sensei-sub">Gia sư ngữ pháp N5–N4</div>
            </div>
            {isLoggedIn && <SenseiQuota refreshKey={aiCalls} />}
            <button type="button" className="sensei-close" onClick={() => setOpen(false)} aria-label="Đóng">
              <CrossIcon size={18} />
            </button>
          </div>

          {ctx?.grammarPointId && (
            <div className="sensei-ctx">
              <span>Đang hỏi về: <b>{ctx.title || ctx.grammarPointId}</b></span>
              <button type="button" onClick={() => setCtx(null)} aria-label="Xóa ngữ cảnh điểm ngữ pháp">×</button>
            </div>
          )}

          {!isLoggedIn ? (
            <div className="sensei-login">
              <p>Đăng nhập để trò chuyện với Sensei AI.</p>
              <Link to="/dang-nhap" className="btn btn-primary btn-sm" onClick={() => setOpen(false)}>
                Đăng nhập
              </Link>
            </div>
          ) : (
            <>
              <div className="sensei-body" ref={bodyRef}>
                {messages.length === 0 && (
                  <div className="sensei-welcome">
                    <p>Chào bạn! Mình là <b>Sensei AI</b> — hỏi mình bất cứ điều gì về ngữ pháp Minna no Nihongo nhé.</p>
                    <div className="sensei-suggest">
                      {SUGGESTIONS.map((s) => (
                        <button key={s} type="button" onClick={() => send(s)}>{s}</button>
                      ))}
                    </div>
                  </div>
                )}
                {messages.map((m) => (
                  <MessageBubble key={m.id} msg={m} onRetry={() => send(m.payload?.message)} />
                ))}
                {sending && (
                  <div className="sensei-msg ai" aria-label="Sensei đang trả lời">
                    <span className="sensei-typing"><i /><i /><i /></span>
                  </div>
                )}
              </div>
              <form className="sensei-input" onSubmit={(e) => { e.preventDefault(); send(); }}>
                <textarea
                  ref={inputRef}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
                  }}
                  placeholder="Hỏi về ngữ pháp… (Enter để gửi)"
                  rows={2}
                  maxLength={MAX_LEN + 100}
                  disabled={sending}
                  aria-label="Nhập câu hỏi cho Sensei AI"
                />
                <button type="submit" className="sensei-send" disabled={sending || !input.trim()} aria-label="Gửi câu hỏi">
                  <SendIcon size={18} />
                </button>
              </form>
            </>
          )}
        </div>
      )}
    </>
  );
}
