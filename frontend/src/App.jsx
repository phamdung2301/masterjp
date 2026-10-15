import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext.jsx';
import { AdminRoute, ProtectedRoute } from './components/ProtectedRoute.jsx';
import Header from './components/Header.jsx';
import Footer from './components/Footer.jsx';
import AdminLayout from './components/AdminLayout.jsx';
import Home from './pages/Home.jsx';
import Lessons from './pages/Lessons.jsx';
import LessonDetail from './pages/LessonDetail.jsx';
import Dictionary from './pages/Dictionary.jsx';
import Flashcard from './pages/Flashcard.jsx';
import Quiz from './pages/Quiz.jsx';
import Typing from './pages/Typing.jsx';
import ReviewDue from './pages/ReviewDue.jsx';
import Sets from './pages/Sets.jsx';
import SetDetail from './pages/SetDetail.jsx';
import Kanji from './pages/Kanji.jsx';
import KanjiDetail from './pages/KanjiDetail.jsx';
import KanjiFlashcard from './pages/KanjiFlashcard.jsx';
import QuickPractice from './pages/QuickPractice.jsx';
import Explore from './pages/Explore.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import AdminOverview from './pages/admin/AdminOverview.jsx';
import AdminLessons from './pages/admin/AdminLessons.jsx';
import AdminWords from './pages/admin/AdminWords.jsx';
import AdminImport from './pages/admin/AdminImport.jsx';
import AdminUsers from './pages/admin/AdminUsers.jsx';
import AdminAudit from './pages/admin/AdminAudit.jsx';
import './index.css';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <div className="app-shell">
          <Header />
          <main className="main-content">
            <Routes>
              {/* Public: guest xem read-only */}
              <Route path="/" element={<Home />} />
              <Route path="/bai-hoc" element={<Lessons />} />
              <Route path="/bai-hoc/:id" element={<LessonDetail />} />
              <Route path="/tu-dien" element={<Dictionary />} />
              <Route path="/kham-pha" element={<Explore />} />

              {/* F-B: kanji — public, xem read-only */}
              <Route path="/kanji" element={<Kanji />} />
              <Route path="/kanji/:char" element={<KanjiDetail />} />

              {/* Auth */}
              <Route path="/dang-nhap" element={<Login />} />
              <Route path="/dang-ky" element={<Register />} />
              <Route path="/quen-mat-khau" element={<ForgotPassword />} />

              {/* Learner: cần đăng nhập */}
              <Route path="/luyen-tap/flashcard" element={<ProtectedRoute><Flashcard /></ProtectedRoute>} />
              <Route path="/luyen-tap/quiz" element={<ProtectedRoute><Quiz /></ProtectedRoute>} />
              <Route path="/luyen-tap/go-phien-am" element={<ProtectedRoute><Typing /></ProtectedRoute>} />
              {/* F-B: flashcard kanji · F-C: luyện tập nhanh */}
              <Route path="/luyen-tap/kanji" element={<ProtectedRoute><KanjiFlashcard /></ProtectedRoute>} />
              <Route path="/luyen-tap-nhanh" element={<ProtectedRoute><QuickPractice /></ProtectedRoute>} />
              <Route path="/on-tap" element={<ProtectedRoute><ReviewDue /></ProtectedRoute>} />
              <Route path="/bo-the" element={<ProtectedRoute><Sets /></ProtectedRoute>} />
              <Route path="/bo-the/:id" element={<ProtectedRoute><SetDetail /></ProtectedRoute>} />
              <Route path="/tong-quan" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />

              {/* Admin: chỉ ROLE_ADMIN */}
              <Route path="/admin" element={<AdminRoute><AdminLayout /></AdminRoute>}>
                <Route index element={<AdminOverview />} />
                <Route path="bai-hoc" element={<AdminLessons />} />
                <Route path="tu-vung" element={<AdminWords />} />
                <Route path="import" element={<AdminImport />} />
                <Route path="nguoi-dung" element={<AdminUsers />} />
                <Route path="nhat-ky" element={<AdminAudit />} />
              </Route>

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
          <Footer />
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}
