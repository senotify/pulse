import type { ReactNode } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { Empty, Spinner } from './components/Status';
import { AuthPage } from './pages/AuthPage';
import { ExplorePage } from './pages/ExplorePage';
import { HomePage } from './pages/HomePage';
import { PostPage } from './pages/PostPage';
import { ProfilePage } from './pages/ProfilePage';

export function App() {
  const { me, loading } = useAuth();
  if (loading) return <Spinner />;

  const guest = (page: ReactNode) => (me ? <Navigate to="/" replace /> : page);
  return (
    <Routes>
      <Route path="/login" element={guest(<AuthPage mode="login" />)} />
      <Route path="/register" element={guest(<AuthPage mode="register" />)} />
      <Route element={me ? <Layout /> : <Navigate to="/login" replace />}>
        <Route index element={<HomePage />} />
        <Route path="explore" element={<ExplorePage />} />
        <Route path="u/:username" element={<ProfilePage />} />
        <Route path="p/:id" element={<PostPage />} />
        <Route
          path="*"
          element={
            <Empty title="Page not found">
              <Link to="/" className="bold">Go home</Link>
            </Empty>
          }
        />
      </Route>
    </Routes>
  );
}
