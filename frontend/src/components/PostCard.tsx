import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { interactions } from '../api/interactions';
import type { Features, PostView, Surface } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { compactCount, timeAgo } from '../lib/format';
import { Avatar } from './Avatar';
import { HeartIcon, ReplyIcon, RepostIcon, SparkIcon, TrashIcon } from './Icons';
import { SignalBars } from './SignalBars';

interface Props {
  post: PostView;
  surface: Surface;
  position?: number;
  modelVersion?: string;
  reason?: string;
  features?: Features;
  score?: number;
}

/**
 * One post. For a repost, the card shows the original with a "reposted by" line, and actions
 * apply to the original (the API does the same).
 */
export function PostCard({ post, surface, position, modelVersion, reason, features, score }: Props) {
  const { me } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const ref = useRef<HTMLElement>(null);
  const [shown, setShown] = useState<PostView>(post.repostOf ?? post);
  const [hidden, setHidden] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => setShown(post.repostOf ?? post), [post]);
  useImpression(ref, () =>
    interactions.track({ postId: shown.id, type: 'IMPRESSION', surface, position, modelVersion }),
  );

  if (hidden) return null;

  const isOwnRepost = post.repostOf !== null && post.author.id === me?.id;
  const isOwnPost = shown.author.id === me?.id;

  function open() {
    if (window.getSelection()?.toString()) return; // let people select text
    interactions.track({ postId: shown.id, type: 'CLICK', surface, position, modelVersion });
    navigate(`/p/${shown.id}`);
  }

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

  const toggleLike = (e: MouseEvent) => act(e, () => api.setLike(shown.id, !shown.likedByMe));
  const toggleRepost = (e: MouseEvent) =>
    act(e, async () => {
      const updated = await api.setRepost(shown.id, !shown.repostedByMe);
      if (isOwnRepost && shown.repostedByMe) setHidden(true); // undoing the repost this card represents
      queryClient.invalidateQueries({ queryKey: ['user-posts', me?.username] });
      return updated;
    });
  const remove = (e: MouseEvent) =>
    act(e, async () => {
      await api.deletePost(shown.id);
      setHidden(true);
      queryClient.invalidateQueries({ queryKey: ['profile', me?.username] });
    });

  return (
    <article ref={ref} className={`card post-wrap ${post.repostOf ? 'is-repost' : ''}`} onClick={open}>
      {post.repostOf && (
        <div className="post-context">
          <RepostIcon />
          <Link to={`/u/${post.author.username}`} onClick={(e) => e.stopPropagation()}>
            {post.author.id === me?.id ? 'You' : post.author.displayName} reposted
          </Link>
        </div>
      )}
      <div className="post">
        <Link to={`/u/${shown.author.username}`} onClick={(e) => e.stopPropagation()}>
          <Avatar user={shown.author} />
        </Link>
        <div className="post-body">
          <div className="post-meta">
            <Link
              to={`/u/${shown.author.username}`}
              className="name truncate"
              onClick={(e) => e.stopPropagation()}
            >
              {shown.author.displayName}
            </Link>
            <span className="handle faint truncate">@{shown.author.username}</span>
            <span className="faint">·</span>
            <time className="faint small" dateTime={shown.createdAt} title={new Date(shown.createdAt).toLocaleString()}>
              {timeAgo(shown.createdAt)}
            </time>
          </div>
          {shown.replyToId && (
            <Link to={`/p/${shown.replyToId}`} className="faint small" onClick={(e) => e.stopPropagation()}>
              Replying to a post
            </Link>
          )}
          <div className="post-content">{shown.content}</div>
          {reason && (
            <div className="reason">
              <SparkIcon width={13} height={13} /> {reason}
            </div>
          )}
          {features && score !== undefined && <SignalBars features={features} score={score} />}
          <div className="actions">
            <button className="action" aria-label="Reply" onClick={(e) => (e.stopPropagation(), open())}>
              <ReplyIcon /> {compactCount(shown.replyCount)}
            </button>
            <button
              className={`action repost ${shown.repostedByMe ? 'on' : ''}`}
              aria-label={shown.repostedByMe ? 'Undo repost' : 'Repost'}
              aria-pressed={shown.repostedByMe}
              onClick={toggleRepost}
              disabled={busy}
            >
              <RepostIcon /> {compactCount(shown.repostCount)}
            </button>
            <button
              className={`action like ${shown.likedByMe ? 'on' : ''}`}
              aria-label={shown.likedByMe ? 'Unlike' : 'Like'}
              aria-pressed={shown.likedByMe}
              onClick={toggleLike}
              disabled={busy}
            >
              <HeartIcon filled={shown.likedByMe} /> {compactCount(shown.likeCount)}
            </button>
            {isOwnPost && (
              <button className="action delete" aria-label="Delete post" onClick={remove} disabled={busy}>
                <TrashIcon />
              </button>
            )}
          </div>
        </div>
      </div>
    </article>
  );
}

/** Fires once when at least half the card has been visible for 600 ms: a real view, not a scroll-past. */
function useImpression(ref: React.RefObject<HTMLElement>, onView: () => void) {
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
