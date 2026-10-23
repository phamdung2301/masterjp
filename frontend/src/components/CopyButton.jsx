import { useState } from 'react';
import { CheckIcon, CopyIcon } from './icons.jsx';

// Nút sao chép vào clipboard (F-044: số tài khoản, nội dung chuyển khoản).
// Clipboard API cần HTTPS — fallback qua textarea khi không khả dụng.
export default function CopyButton({ text, label = 'Sao chép', big = false }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Không báo lỗi ồn ào — user vẫn thấy text để chép tay.
    }
  };

  return (
    <button
      type="button"
      className={`btn btn-sm ${copied ? 'btn-primary' : 'btn-outline'}${big ? ' btn-lg' : ''} copy-btn`}
      onClick={copy}
      aria-label={`${label}: ${text}`}
    >
      {copied ? <CheckIcon size={16} /> : <CopyIcon size={16} />}
      {copied ? 'Đã sao chép!' : label}
    </button>
  );
}
