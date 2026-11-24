import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Suggestion } from '../api/types';
import { Avatar } from './Avatar';
import { SearchIcon } from './Icons';

export function RightSidebar() {
  return (
    <aside className="aside">
      <div className="aside-inner">
        <SearchBox />
        <Trends />
        <WhoToFollow />
        <footer className="footer-links">
          Pulse is an open-source portfolio project ·{' '}
          <a href="https://github.com/senotify/pulse" target="_blank" rel="noopener noreferrer">
            Source
          </a>
        </footer>
      </div>
    </aside>
  );
}

export function SearchBox({ initial = '' }: { initial?: string }) {
  const navigate = useNavigate();
  const [q, setQ] = useState(initial);
  function submit(e: FormEvent) {
    e.preventDefault();
    if (q.trim().length >= 2) navigate(`/explore?q=${encodeURIComponent(q.trim())}`);
  }
  return (
    <form className="search" role="search" onSubmit={submit}>
      <SearchIcon />
      <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search Pulse" aria-label="Search Pulse" />
    </form>
  );
}

export function Trends({ limit }: { limit?: number }) {
  const trends = useQuery({ queryKey: ['trends'], queryFn: api.trends, staleTime: 60_000 });
  const items = (trends.data ?? []).slice(0, limit);
  return (
    <section className="box" aria-labelledby="trends-title">
      <h2 id="trends-title">Trending on Pulse</h2>
      {trends.isPending && <div className="box-row faint">Loading…</div>}
      {trends.isSuccess && items.length === 0 && (
        <div className="box-row faint">No hashtags in the last 24 hours. Start one!</div>
      )}
      {items.map((trend, i) => (
        <Link key={trend.tag} to={`/explore?q=${encodeURIComponent(trend.tag)}`} className="box-row">
          <div className="trend-meta">{i + 1} · Trending</div>
          <div className="trend-tag">{trend.tag}</div>
          <div className="trend-meta">
            {trend.posts} {trend.posts === 1 ? 'post' : 'posts'}
          </div>
        </Link>
      ))}
    </section>
  );
}

export function WhoToFollow() {
  const suggestions = useQuery({ queryKey: ['suggestions'], queryFn: () => api.suggestions(3), staleTime: 60_000 });
  if (suggestions.isSuccess && suggestions.data.length === 0) return null;
  return (
    <section className="box" aria-labelledby="wtf-title">
      <h2 id="wtf-title">Who to follow</h2>
      {suggestions.isPending && <div className="box-row faint">Loading…</div>}
      {suggestions.data?.map((s) => <SuggestionRow key={s.id} suggestion={s} />)}
    </section>
  );
}

function SuggestionRow({ suggestion }: { suggestion: Suggestion }) {
  const queryClient = useQueryClient();
  const [followed, setFollowed] = useState(false);
  const follow = useMutation({
    mutationFn: () => api.setEdge(suggestion.username, 'follow', !followed),
    onSuccess: () => {
      setFollowed((f) => !f);
      queryClient.invalidateQueries({ queryKey: ['timeline'] });
      queryClient.invalidateQueries({ queryKey: ['profile', suggestion.username] });
    },
  });
  const context =
    suggestion.mutuals > 0
      ? `Followed by ${suggestion.mutuals} you follow`
      : suggestion.followers > 0
        ? `${suggestion.followers} followers`
        : 'New on Pulse';

  return (
    <div className="box-row who">
      <Link to={`/u/${suggestion.username}`}>
        <Avatar user={suggestion} size={40} />
      </Link>
      <Link to={`/u/${suggestion.username}`} className="grow">
        <div className="bold truncate">{suggestion.displayName}</div>
        <div className="muted small truncate">
          @{suggestion.username} · {context}
        </div>
      </Link>
      <button
        className={`btn btn-small ${followed ? 'btn-outline' : ''}`}
        onClick={() => follow.mutate()}
        disabled={follow.isPending}
      >
        {followed ? 'Following' : 'Follow'}
      </button>
    </div>
  );
}
