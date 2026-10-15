import { useState } from 'react';
import { adminImportConfirm, adminImportValidate } from '../../api/admin.js';
import { getErrorMessage } from '../../api/client.js';

// US-011 (F-032): import Excel — 2 bước: upload → xem báo cáo validate → xác nhận.
export default function AdminImport() {
  const [file, setFile] = useState(null);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState('');

  const handleValidate = async (e) => {
    e.preventDefault();
    setError('');
    setDone('');
    setReport(null);
    if (!file) { setError('Vui lòng chọn file Excel.'); return; }
    setLoading(true);
    try {
      const r = await adminImportValidate(file);
      setReport(r);
    } catch (err) {
      setError(getErrorMessage(err, 'Không đọc được file. Kiểm tra định dạng file.'));
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async () => {
    setError('');
    setConfirming(true);
    try {
      // Gửi lại các dòng hợp lệ từ bước validate (stateless — không dùng importToken).
      const r = await adminImportConfirm(report.rows || []);
      const doneMsg = `Đã import ${r.imported ?? 0} từ${(r.skipped ?? 0) > 0 ? `, bỏ qua ${r.skipped} dòng` : ''}.`;
      setDone(doneMsg);
      setReport(null);
      setFile(null);
    } catch (err) {
      setError(getErrorMessage(err, 'Xác nhận import thất bại.'));
    } finally {
      setConfirming(false);
    }
  };

  return (
    <div>
      <h2>Import Excel</h2>
      <p className="muted">
        File theo mẫu: cột lessonNo, section, kana, kanji, romaji, meaningVi, pos, exampleJa, exampleVi.
        Hệ thống kiểm tra trước — bạn xem báo cáo lỗi rồi mới xác nhận ghi vào DB.
      </p>
      {error && <div className="alert alert-error">{error}</div>}
      {done && <div className="alert alert-success">{done}</div>}

      <form className="card form-inline" onSubmit={handleValidate}>
        <input type="file" accept=".xlsx,.xls" onChange={(e) => setFile(e.target.files?.[0] || null)} />
        <button type="submit" className="btn btn-primary btn-sm" disabled={loading}>
          {loading ? 'Đang kiểm tra…' : 'Tải lên & kiểm tra'}
        </button>
      </form>

      {report && (
        <div className="card">
          <h3>Kết quả kiểm tra</h3>
          <div className="stat-grid">
            <div className="stat-card"><div className="stat-value">{report.totalRows ?? 0}</div><div className="stat-label">Tổng dòng</div></div>
            <div className="stat-card"><div className="stat-value">{report.validRows ?? 0}</div><div className="stat-label">Dòng hợp lệ</div></div>
            <div className="stat-card"><div className="stat-value">{(report.totalRows ?? 0) - (report.validRows ?? 0)}</div><div className="stat-label">Dòng lỗi</div></div>
          </div>
          {(report.errors || []).length > 0 && (
            <div className="admin-table-wrap">
              <table className="admin-table">
                <thead><tr><th>Dòng</th><th>Lỗi</th></tr></thead>
                <tbody>
                  {report.errors.map((er, i) => (
                    <tr key={i}><td>{er.row}</td><td>{er.message}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="row-actions">
            <button type="button" className="btn btn-primary" onClick={handleConfirm} disabled={confirming || (report.validRows ?? 0) === 0}>
              {confirming ? 'Đang import…' : `Xác nhận import ${report.validRows ?? 0} từ`}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
