import { useCallback, useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from './Avatar';
import { ComposeModal } from './ComposeModal';
import { DotsIcon, HomeIcon, PenIcon, PulseMark, SearchIcon, UserIcon } from './Icons';
import { RightSidebar } from './RightSidebar';

export function Layout() {
  const { me, signOut } = useAuth();
  const [composing, setComposing] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menu = useRef<HTMLDivElement>(null);
  const location = useLocation();
  const closeCompose = useCallback(() => setComposing(false), []);

  useEffect(() => setMenuOpen(false), [location.pathname]);
  useEffect(() => {
    if (!menuOpen) return;
    const onDown = (e: MouseEvent) => {
      if (menu.current && !menu.current.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [menuOpen]);

  if (!me) return null;

  const links = (
    <>
      <NavLink to="/" end className="nav-link">
        <HomeIcon /> <span>Home</span>
      </NavLink>
      <NavLink to="/explore" className="nav-link">
        <SearchIcon /> <span>Explore</span>
      </NavLink>
      <NavLink to={`/u/${me.username}`} className="nav-link">
        <UserIcon /> <span>Profile</span>
      </NavLink>
    </>
  );

  return (
    <div className="shell">
      <header className="sidebar">
        <div className="sidebar-inner" ref={menu} style={{ position: 'sticky' }}>
          <NavLink to="/" className="logo" aria-label="Pulse home">
            <PulseMark />
          </NavLink>
          <nav className="nav" aria-label="Main">
            {links}
          </nav>
          <button className="btn btn-primary post-button" onClick={() => setComposing(true)}>
            <span className="label">Post</span>
            <PenIcon className="icon" />
          </button>
          {menuOpen && (
            <div className="account-menu" role="menu">
              <button role="menuitem" onClick={signOut}>
                Log out @{me.username}
              </button>
            </div>
          )}
          <button className="account" onClick={() => setMenuOpen((o) => !o)} aria-haspopup="menu" aria-expanded={menuOpen}>
            <Avatar user={me} size={40} />
            <div className="names">
              <div className="bold truncate">{me.displayName}</div>
              <div className="muted truncate">@{me.username}</div>
            </div>
            <DotsIcon className="dots" width={18} height={18} />
          </button>
        </div>
      </header>

      <main className="timeline">
        <Outlet />
      </main>

      <RightSidebar />

      <nav className="mobile-nav" aria-label="Main">
        {links}
      </nav>
      <button className="fab" onClick={() => setComposing(true)} aria-label="New post">
        <PenIcon />
      </button>

      {composing && <ComposeModal onClose={closeCompose} />}
    </div>
  );
}
