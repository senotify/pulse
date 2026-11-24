import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from './Avatar';
import { HomeIcon, LogoutIcon, PulseMark, UserIcon } from './Icons';

export function Layout() {
  const { me, signOut } = useAuth();
  if (!me) return null;

  const links = (
    <>
      <NavLink to="/" end className="nav-link">
        <HomeIcon /> <span>Home</span>
      </NavLink>
      <NavLink to={`/u/${me.username}`} className="nav-link">
        <UserIcon /> <span>Profile</span>
      </NavLink>
    </>
  );

  return (
    <div className="shell">
      <aside className="sidebar">
        <NavLink to="/" className="brand">
          <PulseMark className="brand-mark" /> Pulse
        </NavLink>
        {links}
        <div className="sidebar-footer">
          <NavLink to={`/u/${me.username}`} className="me-chip">
            <Avatar user={me} size={36} />
            <div className="names">
              <div className="bold truncate">{me.displayName}</div>
              <div className="faint small truncate">@{me.username}</div>
            </div>
          </NavLink>
          <button className="nav-link" style={{ background: 'none', border: 'none' }} onClick={signOut}>
            <LogoutIcon /> <span>Sign out</span>
          </button>
        </div>
      </aside>
      <main>
        <Outlet />
      </main>
      <nav className="mobile-nav" aria-label="Main">
        {links}
        <button className="nav-link" style={{ background: 'none', border: 'none' }} onClick={signOut} aria-label="Sign out">
          <LogoutIcon />
        </button>
      </nav>
    </div>
  );
}
