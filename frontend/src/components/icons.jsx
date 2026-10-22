// icons.jsx — icon SVG vẽ tay (stroke tròn), thay thế emoji dùng làm icon (A2).
// Không thêm dependency; dùng chung cho Header, flashcard, quiz, typing, hub.
function Svg({ children, size = 20, ...rest }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {children}
    </svg>
  );
}

export function SpeakerIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M4 9.5v5h3.5L13 19V5L7.5 9.5H4z" />
      <path d="M16 9.2a4 4 0 0 1 0 5.6" />
      <path d="M18.4 6.8a7.4 7.4 0 0 1 0 10.4" />
    </Svg>
  );
}

export function FlameIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M12 2.5c.9 3.6-2.3 5.4-3.6 7.4C7.2 11.9 6.8 13.3 6.8 14.8a5.2 5.2 0 0 0 10.4 0c0-1.6-.6-3-1.4-4.2-.3 1-1 1.9-2 2.4.6-2.8-.2-7-1.8-10.5z" />
    </Svg>
  );
}

export function CheckIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M4.5 12.5l5 5L19.5 7" />
    </Svg>
  );
}

export function CrossIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M6 6l12 12M18 6L6 18" />
    </Svg>
  );
}

export function RestartIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M20 4v5h-5" />
      <path d="M4 20v-5h5" />
      <path d="M19.5 9a8 8 0 0 0-13.8-3.2L4 7.5" />
      <path d="M4.5 15a8 8 0 0 0 13.8 3.2L20 16.5" />
    </Svg>
  );
}

export function CardIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M6 3.5h8.5L19 8v12.5H6z" />
      <path d="M14.5 3.5V8H19" />
      <path d="M9.5 12.5h5M9.5 15.5h5" />
    </Svg>
  );
}

export function PencilIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M4 20l1.2-4.2L16.7 4.3a2.05 2.05 0 0 1 2.9 2.9L8.1 18.7 4 20z" />
      <path d="M14.5 6.5l3 3" />
    </Svg>
  );
}

export function TrophyIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M8 4h8v4.5a4 4 0 0 1-8 0V4z" />
      <path d="M8 5.5H4.5a3.5 3.5 0 0 0 3.6 4.7M16 5.5h3.5a3.5 3.5 0 0 1-3.6 4.7" />
      <path d="M12 12.5v3" />
      <path d="M8.5 19.5h7" />
    </Svg>
  );
}

export function GearIcon({ size }) {
  return (
    <Svg size={size}>
      <circle cx="12" cy="12" r="3.4" />
      <path d="M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.2 5.2l1.8 1.8M17 17l1.8 1.8M18.8 5.2L17 7M7 17l-1.8 1.8" />
    </Svg>
  );
}

export function BookIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M5 4.5h10.5A3.5 3.5 0 0 1 19 8v11.5H8.5A3.5 3.5 0 0 1 5 16V4.5z" />
      <path d="M5 16a3.5 3.5 0 0 1 3.5-3.5H19" />
    </Svg>
  );
}

export function ChartIcon({ size }) {
  return (
    <Svg size={size}>
      <path d="M4 20h16" />
      <path d="M7 20v-6M12 20V6M17 20v-9" />
    </Svg>
  );
}

export function UsersIcon({ size }) {
  return (
    <Svg size={size}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19.5a5.5 5.5 0 0 1 11 0" />
      <path d="M15.5 5.4a3.2 3.2 0 0 1 0 5.6M17.8 14.4a5.5 5.5 0 0 1 2.7 5.1" />
    </Svg>
  );
}
