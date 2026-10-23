// Heatmap hoc tap (F-042) — CSS grid kieu GitHub, can theo tuan lich (bat dau Thu Hai).
// days: [{ date: 'yyyy-MM-dd', minutes, sessions }] theo thu tu thoi gian (cu -> moi).
// 4 muc mau washi theo minutes: 0 / 1-15 / 16-45 / >45.

const WEEKDAY_LABELS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

// 'yyyy-MM-dd' -> 'dd/MM/yyyy' cho tooltip tieng Viet.
function fmtDate(iso) {
  const [y, m, d] = String(iso).split('-');
  return `${d}/${m}/${y}`;
}

function levelOf(minutes) {
  if (minutes <= 0) return 'l0';
  if (minutes <= 15) return 'l1';
  if (minutes <= 45) return 'l2';
  return 'l3';
}

function buildColumns(days) {
  const cols = [];
  let col = [];
  days.forEach((d) => {
    const jsDay = new Date(`${d.date}T00:00:00`).getDay(); // 0=CN .. 6=T7
    const monIdx = (jsDay + 6) % 7; // 0=T2 .. 6=CN
    if (monIdx === 0 && col.length > 0) {
      cols.push(col);
      col = [];
    }
    col.push({ ...d, monIdx });
  });
  if (col.length > 0) cols.push(col);
  if (cols.length === 0) return [];
  const headPadCount = cols[0][0].monIdx;
  const lastCol = cols[cols.length - 1];
  const tailPadCount = 6 - lastCol[lastCol.length - 1].monIdx;
  return cols.map((c, i) => {
    if (i === 0 && headPadCount > 0) {
      return [...Array.from({ length: headPadCount }, () => null), ...c];
    }
    if (i === cols.length - 1 && tailPadCount > 0) {
      return [...c, ...Array.from({ length: tailPadCount }, () => null)];
    }
    return c;
  });
}

export default function Heatmap({ days, weeks }) {
  const list = Array.isArray(days) ? days : [];
  if (list.length === 0) return null;
  const cols = buildColumns(list);

  return (
    <section className="heatmap-wrap" aria-label="Bản đồ học tập">
      <h3 style={{ marginBottom: 2 }}>Bản đồ học tập</h3>
      <p className="muted" style={{ marginTop: 0 }}>
        {weeks || list.length} tuần gần nhất — mỗi ô là một ngày học
      </p>
      <div className="heatmap-grid">
        <div className="heatmap-labels" aria-hidden="true">
          {WEEKDAY_LABELS.map((l) => (
            <span key={l}>{l}</span>
          ))}
        </div>
        {cols.map((c, i) => (
          <div key={i} className="heatmap-col">
            {c.map((d, j) =>
              d ? (
                <span
                  key={d.date}
                  className={`heatmap-cell ${levelOf(d.minutes)}`}
                  title={`${fmtDate(d.date)} · ${d.minutes} phút · ${d.sessions} phiên`}
                />
              ) : (
                <span key={`pad-${j}`} className="heatmap-cell pad" />
              )
            )}
          </div>
        ))}
      </div>
      <div className="heatmap-legend" aria-hidden="true">
        <span>Ít</span>
        <span className="heatmap-cell l0" />
        <span className="heatmap-cell l1" />
        <span className="heatmap-cell l2" />
        <span className="heatmap-cell l3" />
        <span>Nhiều</span>
      </div>
    </section>
  );
}
