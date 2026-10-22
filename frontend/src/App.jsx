import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext.jsx';
import { AdminRoute, ProtectedRoute } from './components/ProtectedRoute.jsx';
import Header from './components/Header.jsx';
import Footer from './components/Footer.jsx';
import AdminLayout from './components/AdminLayout.jsx';
import Home from './pages/Home.jsx';
import Hoc from './pages/Hoc.jsx';
import LessonHub from './pages/LessonHub.jsx';
import CaNhan from './pages/CaNhan.jsx';
import Dictionary from './pages/Dictionary.jsx';
import Flashcard from './pages/Flashcard.jsx';
import Quiz from './pages/Quiz.jsx';
import Typing from './pages/Typing.jsx';
import ReviewDue from './pages/ReviewDue.jsx';
import Kanji from './pages/Kanji.jsx';
import Grammar from './pages/Grammar.jsx';
import KanjiDetail from './pages/KanjiDetail.jsx';
import KanjiFlashcard from './pages/KanjiFlashcard.jsx';
import KanjiQuiz from './pages/KanjiQuiz.jsx';
import Explore from './pages/Explore.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import AdminOverview from './pages/admin/AdminOverview.jsx';
import AdminLessons from './pages/admin/AdminLessons.jsx';
import ErrorBoundary from './components/ErrorBoundary.jsx';
import AdminWords from './pages/admin/AdminWords.jsx';
import AdminImport from './pages/admin/AdminImport.jsx';
import AdminUsers from './pages/admin/AdminUsers.jsx';
import AdminAudit from './pages/admin/AdminAudit.jsx';
import './index.css';

// DOC-10 §4: redirect giữ bookmark cũ — /bai-hoc/:id → /hoc/bai/:id.
function BaiHocRedirect() {
  const { id } = useParams();
  return <Navigate to={`/hoc/bai/${id}`} replace />;
}

// DOC-10 §4: /bo-the/:id → /ca-nhan/bo-the/:id (tab Bộ thẻ + chi tiết bộ).
function BoTheDetailRedirect() {
  const { id } = useParams();
  return <Navigate to={`/ca-nhan/bo-the/${id}`} replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="app-shell">
          <Header />
          <main className="main-content">
            {/* ErrorBoundary: mỗi route bọc riêng — 1 trang crash không trắng toàn app. */}
            <ErrorBoundary>
            <Routes>
              {/* Public */}
              <Route path="/" element={<ErrorBoundary><Home /></ErrorBoundary>} />
              <Route path="/hoc" element={<ErrorBoundary><Hoc /></ErrorBoundary>} />
              <Route path="/hoc/bai/:id" element={<ErrorBoundary><LessonHub /></ErrorBoundary>} />
              <Route path="/tu-dien" element={<ErrorBoundary><Dictionary /></ErrorBoundary>} />
              <Route path="/kham-pha" element={<ErrorBoundary><Explore /></ErrorBoundary>} />

              {/* F-B: kanji — public, xem read-only */}
              <Route path="/kanji" element={<ErrorBoundary><Kanji /></ErrorBoundary>} />
              <Route path="/kanji/:char" element={<ErrorBoundary><KanjiDetail /></ErrorBoundary>} />

              {/* Ngữ pháp — public, xem read-only */}
              <Route path="/ngu-phap" element={<ErrorBoundary><Grammar /></ErrorBoundary>} />

              {/* Auth */}
              <Route path="/dang-nhap" element={<ErrorBoundary><Login /></ErrorBoundary>} />
              <Route path="/dang-ky" element={<ErrorBoundary><Register /></ErrorBoundary>} />
              <Route path="/quen-mat-khau" element={<ErrorBoundary><ForgotPassword /></ErrorBoundary>} />

              {/* Learner: cần đăng nhập */}
              <Route path="/on-tap" element={<ErrorBoundary><ProtectedRoute><ReviewDue /></ProtectedRoute></ErrorBoundary>} />
              <Route path="/ca-nhan" element={<ErrorBoundary><ProtectedRoute><CaNhan /></ProtectedRoute></ErrorBoundary>} />
              <Route path="/ca-nhan/bo-the/:id" element={<ErrorBoundary><ProtectedRoute><CaNhan initialTab="bo-the" /></ProtectedRoute></ErrorBoundary>} />

              {/* Luyện tập theo IA mới (A3):
                  - Trong bài: LessonHub nhúng component (không qua route).
                  - Trang /kanji: flow chọn chữ → luyện (R3) đi qua các route này,
                    thay cho các route /luyen-tap/* cũ đã redirect về /hoc. */}
              <Route path="/kanji/the" element={<ErrorBoundary><ProtectedRoute><KanjiFlashcard /></ProtectedRoute></ErrorBoundary>} />
              <Route path="/kanji/quiz" element={<ErrorBoundary><ProtectedRoute><KanjiQuiz /></ProtectedRoute></ErrorBoundary>} />
              <Route path="/luyen-nhanh/the" element={<ErrorBoundary><ProtectedRoute><Flashcard /></ProtectedRoute></ErrorBoundary>} />
              <Route path="/luyen-nhanh/quiz" element={<ErrorBoundary><ProtectedRoute><Quiz /></ProtectedRoute></ErrorBoundary>} />
              <Route path="/luyen-nhanh/go" element={<ErrorBoundary><ProtectedRoute><Typing /></ProtectedRoute></ErrorBoundary>} />

              {/* DOC-10 §4: route cũ không 404 — redirect 301 về route mới tương đương */}
              <Route path="/bai-hoc" element={<Navigate to="/hoc" replace />} />
              <Route path="/bai-hoc/:id" element={<BaiHocRedirect />} />
              <Route path="/luyen-tap/flashcard" element={<Navigate to="/hoc" replace />} />
              <Route path="/luyen-tap/quiz" element={<Navigate to="/hoc" replace />} />
              <Route path="/luyen-tap/go-phien-am" element={<Navigate to="/hoc" replace />} />
              <Route path="/luyen-tap/kanji" element={<Navigate to="/hoc" replace />} />
              <Route path="/luyen-tap/kanji-quiz" element={<Navigate to="/hoc" replace />} />
              <Route path="/luyen-tap-nhanh" element={<Navigate to="/hoc" replace />} />
              <Route path="/tong-quan" element={<Navigate to="/ca-nhan" replace />} />
              <Route path="/bo-the" element={<Navigate to="/ca-nhan?tab=bo-the" replace />} />
              <Route path="/bo-the/:id" element={<BoTheDetailRedirect />} />

              {/* Admin: chỉ ROLE_ADMIN */}
              <Route path="/admin" element={<ErrorBoundary><AdminRoute><AdminLayout /></AdminRoute></ErrorBoundary>}>
                <Route index element={<AdminOverview />} />
                <Route path="bai-hoc" element={<AdminLessons />} />
                <Route path="tu-vung" element={<AdminWords />} />
                <Route path="import" element={<AdminImport />} />
                <Route path="nguoi-dung" element={<AdminUsers />} />
                <Route path="nhat-ky" element={<AdminAudit />} />
              </Route>

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
            </ErrorBoundary>
          </main>
          <Footer />
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}
