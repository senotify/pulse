import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { Avatar } from './Avatar';
import { SearchIcon } from './Icons';

/** Waits until the value has stopped changing for {@code ms} before returning it. */
function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(timer);
  }, [value, ms]);
  return debounced;
}

/**
 * The search box with people suggestions as you type. Row 0 searches posts for the text;
 * the rows after it open matching profiles. Arrow keys move, Enter opens, Escape closes.
 */
export function SearchBox({ initial = '' }: { initial?: string }) {
  const navigate = useNavigate();
  const [q, setQ] = useState(initial);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const box = useRef<HTMLFormElement>(null);
  const term = useDebounced(q.trim(), 200);

  const people = useQuery({
    queryKey: ['search-users', term],
    queryFn: () => api.searchUsers(term, 6),
    enabled: open && term.replace(/^@/, '').length >= 1,
    staleTime: 30_000,
  });
  const users = term.replace(/^@/, '').length >= 1 ? (people.data ?? []) : [];

  useEffect(() => setActive(0), [term]);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  function go(index: number) {
    setOpen(false);
    if (index > 0 && users[index - 1]) {
      navigate(`/u/${users[index - 1].username}`);
    } else if (q.trim().length >= 2) {
      navigate(`/explore?q=${encodeURIComponent(q.trim())}`);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    go(active);
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    const rows = users.length + 1;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setOpen(true);
      setActive((a) => (a + 1) % rows);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => (a - 1 + rows) % rows);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  }

  const showPanel = open && q.trim().length > 0;
  return (
    <form ref={box} className="search-wrap" role="search" onSubmit={submit}>
      <div className="search">
        <SearchIcon />
        <input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder="Search Pulse"
          aria-label="Search Pulse"
          role="combobox"
          aria-expanded={showPanel}
          aria-controls="search-suggestions"
          autoComplete="off"
        />
      </div>
      {showPanel && (
        <div className="search-panel" id="search-suggestions" role="listbox">
          <button
            type="button"
            role="option"
            aria-selected={active === 0}
            className={`search-row ${active === 0 ? 'active' : ''}`}
            onMouseEnter={() => setActive(0)}
            onClick={() => go(0)}
            disabled={q.trim().length < 2}
          >
            <span className="search-row-icon"><SearchIcon /></span>
            <span className="truncate">Search for "{q.trim()}"</span>
          </button>
          {users.map((user, i) => (
            <button
              type="button"
              role="option"
              key={user.id}
              aria-selected={active === i + 1}
              className={`search-row ${active === i + 1 ? 'active' : ''}`}
              onMouseEnter={() => setActive(i + 1)}
              onClick={() => go(i + 1)}
            >
              <Avatar user={user} size={40} />
              <span className="grow" style={{ textAlign: 'left' }}>
                <span className="bold truncate" style={{ display: 'block' }}>{user.displayName}</span>
                <span className="muted small truncate" style={{ display: 'block' }}>
                  @{user.username}
                  {user.followedByMe && ' · Following'}
                </span>
              </span>
            </button>
          ))}
          {people.isFetching && users.length === 0 && <div className="search-row muted">Searching…</div>}
          {!people.isFetching && people.isSuccess && users.length === 0 && (
            <div className="search-row muted">No people match "{term}"</div>
          )}
        </div>
      )}
    </form>
  );
}
