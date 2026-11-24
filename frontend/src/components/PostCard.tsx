import { useEffect, useRef, useState, type MouseEvent, type RefObject } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { interactions } from '../api/interactions';
import type { Features, PostView, Surface } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { compactCount, timeAgo } from '../lib/format';
import { Avatar } from './Avatar';
import { HeartIcon, ReplyIcon, RepostIcon, SparkIcon, TrashIcon } from './Icons';
import { RichText } from './RichText';
import { SignalBars } from './SignalBars';

interface Tracking {
  surface: Surface;
  position?: number;
  modelVersion?: string;
}

/**
 * Local state and actions for one post. For a repost the card shows the original and every
 * action targets the original, as the API does.
 */
function usePostActions(post: PostView) {
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const [shown, setShown] = useState<PostView>(post.repostOf ?? post);
  const [hidden, setHidden] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => setShown(post.repostOf ?? post), [post]);

  const isOwnRepost = post.repostOf !== null && post.author.id === me?.id;

  async function act(e: MouseEvent, fn: () => Promise<PostView | void>) {
    e.stopPropagation();
    if (busy || !me) return;
    setBusy(true);
    try {
      const updated = await fn();
      if (updated) setShown(updated.repostOf ?? updated);
    } catch {
      // Leave the card as it was; the next refresh shows the truth.
    } finally {
      setBusy(false);
    }
  }

  return {
    shown,
    hidden,
    busy,
    canDelete: shown.author.id === me?.id,
    toggleLike: (e: MouseEvent) => act(e, () => api.setLike(shown.id, !shown.likedByMe)),
    toggleRepost: (e: MouseEvent) =>
      act(e, async () => {
        const updated = await api.setRepost(shown.id, !shown.repostedByMe);
        if (isOwnRepost && shown.repostedByMe) setHidden(true); // undid the repost this row represents
        queryClient.invalidateQueries({ queryKey: ['user-posts', me?.username] });
        return updated;
      }),
    remove: (e: MouseEvent) =>
      act(e, async () => {
        await api.deletePost(shown.id);
        setHidden(true);
        queryClient.invalidateQueries({ queryKey: ['profile', me?.username] });
      }),
  };
}

type Actions = ReturnType<typeof usePostActions>;

function PostActions({ a, onReply, showCounts = true }: { a: Actions; onReply: () => void; showCounts?: boolean }) {
  const { shown, busy } = a;
  const count = (n: number) => (showCounts && n > 0 ? compactCount(n) : '');
  return (
    <div className="actions" onClick={(e) => e.stopPropagation()}>
      <button className="action" aria-label="Reply" onClick={onReply}>
        <span className="bubble"><ReplyIcon /></span>
        {count(shown.replyCount)}
      </button>
      <button
        className={`action repost ${shown.repostedByMe ? 'on' : ''}`}
        aria-label={shown.repostedByMe ? 'Undo repost' : 'Repost'}
        aria-pressed={shown.repostedByMe}
        onClick={a.toggleRepost}
        disabled={busy}
      >
        <span className="bubble"><RepostIcon /></span>
        {count(shown.repostCount)}
      </button>
      <button
        className={`action like ${shown.likedByMe ? 'on' : ''}`}
        aria-label={shown.likedByMe ? 'Unlike' : 'Like'}
        aria-pressed={shown.likedByMe}
        onClick={a.toggleLike}
        disabled={busy}
      >
        <span className="bubble"><HeartIcon filled={shown.likedByMe} /></span>
        {count(shown.likeCount)}
      </button>
      {a.canDelete ? (
        <button className="action delete" aria-label="Delete post" onClick={a.remove} disabled={busy}>
          <span className="bubble"><TrashIcon /></span>
        </button>
      ) : (
        <span className="action delete" aria-hidden="true" />
      )}
    </div>
  );
}

/** A timeline row. */
export function PostCard({
  post,
  surface,
  position,
  modelVersion,
  reason,
  features,
  score,
}: { post: PostView; reason?: string; features?: Features; score?: number } & Tracking) {
  const { me } = useAuth();
  const navigate = useNavigate();
  const ref = useRef<HTMLElement>(null);
  const a = usePostActions(post);
  const { shown } = a;

  useImpression(ref, () => interactions.track({ postId: shown.id, type: 'IMPRESSION', surface, position, modelVersion }));
  if (a.hidden) return null;

  function open() {
    if (window.getSelection()?.toString()) return; // let people select text
    interactions.track({ postId: shown.id, type: 'CLICK', surface, position, modelVersion });
    navigate(`/p/${shown.id}`);
  }

  const stop = (e: MouseEvent) => e.stopPropagation();

  return (
    <article ref={ref} className="post" onClick={open}>
      <div className="post-gutter">
        <Link to={`/u/${shown.author.username}`} onClick={stop}>
          <Avatar user={shown.author} size={48} />
        </Link>
      </div>
      <div className="post-body">
        {post.repostOf && (
          <div className="post-context">
            <RepostIcon />
            <Link to={`/u/${post.author.username}`} onClick={stop}>
              {post.author.id === me?.id ? 'You' : post.author.displayName} reposted
            </Link>
          </div>
        )}
        <div className="post-meta">
          <Link to={`/u/${shown.author.username}`} className="name" onClick={stop}>
            {shown.author.displayName}
          </Link>
          <span className="handle">@{shown.author.username}</span>
          <span className="dot">·</span>
          <time dateTime={shown.createdAt} title={new Date(shown.createdAt).toLocaleString()}>
            {timeAgo(shown.createdAt)}
          </time>
        </div>
        {shown.replyToId && (
          <div className="post-replying small">
            Replying to{' '}
            <Link to={`/p/${shown.replyToId}`} className="link" onClick={stop}>
              a post
            </Link>
          </div>
        )}
        <div className="post-content">
          <RichText text={shown.content ?? ''} />
        </div>
        {reason && (
          <div className="reason">
            <SparkIcon /> {reason}
          </div>
        )}
        {features && score !== undefined && <SignalBars features={features} score={score} />}
        <PostActions a={a} onReply={open} />
      </div>
    </article>
  );
}

/** The focused post at the top of a thread: large text, full timestamp and a stats row. */
export function PostDetail({ post, onReply }: { post: PostView; onReply: () => void }) {
  const ref = useRef<HTMLElement>(null);
  const a = usePostActions(post);
  const { shown } = a;
  useImpression(ref, () => interactions.track({ postId: shown.id, type: 'IMPRESSION', surface: 'POST' }));
  if (a.hidden) return <div className="empty"><h2>Post deleted</h2></div>;

  const created = new Date(shown.createdAt);
  return (
    <article ref={ref} className="post-detail">
      <div className="head">
        <Link to={`/u/${shown.author.username}`}>
          <Avatar user={shown.author} size={48} />
        </Link>
        <div className="grow">
          <Link to={`/u/${shown.author.username}`} className="name">{shown.author.displayName}</Link>
          <div className="muted">@{shown.author.username}</div>
        </div>
      </div>
      {shown.replyToId && (
        <div className="post-replying" style={{ marginTop: -6, marginBottom: 10 }}>
          Replying to <Link to={`/p/${shown.replyToId}`} className="link">a post</Link>
        </div>
      )}
      <div className="post-content">
        <RichText text={shown.content ?? ''} />
      </div>
      <div className="timestamp">
        {created.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })} ·{' '}
        {created.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}
      </div>
      {(shown.repostCount > 0 || shown.likeCount > 0) && (
        <div className="stats">
          {shown.repostCount > 0 && <span><b>{compactCount(shown.repostCount)}</b> Reposts</span>}
          {shown.likeCount > 0 && <span><b>{compactCount(shown.likeCount)}</b> Likes</span>}
        </div>
      )}
      <PostActions a={a} onReply={onReply} showCounts={false} />
    </article>
  );
}

/** Fires once when at least half the element has been visible for 600 ms: a real view, not a scroll-past. */
function useImpression(ref: RefObject<HTMLElement>, onView: () => void) {
  const callback = useRef(onView);
  callback.current = onView;

  useEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === 'undefined') return;
    let timer: ReturnType<typeof setTimeout> | null = null;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          timer = setTimeout(() => {
            callback.current();
            observer.disconnect();
          }, 600);
        } else if (timer) {
          clearTimeout(timer);
          timer = null;
        }
      },
      { threshold: 0.5 },
    );
    observer.observe(el);
    return () => {
      if (timer) clearTimeout(timer);
      observer.disconnect();
    };
  }, [ref]);
}
