import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { PostView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from './Avatar';

const MAX = 280;

export function Composer({
  replyToId,
  placeholder = "What's happening?",
  autoFocus = false,
  onPosted,
}: {
  replyToId?: number;
  placeholder?: string;
  autoFocus?: boolean;
  onPosted?: (post: PostView) => void;
}) {
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const area = useRef<HTMLTextAreaElement>(null);

  // Grow with the text instead of scrolling inside a tiny box.
  useEffect(() => {
    const el = area.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [text]);

  const mutation = useMutation({
    mutationFn: () => api.createPost(text.trim(), replyToId),
    onSuccess: (post) => {
      setText('');
      queryClient.invalidateQueries({ queryKey: ['timeline'] });
      queryClient.invalidateQueries({ queryKey: ['user-posts', post.author.username] });
      queryClient.invalidateQueries({ queryKey: ['profile', post.author.username] });
      queryClient.invalidateQueries({ queryKey: ['trends'] });
      if (replyToId) {
        queryClient.invalidateQueries({ queryKey: ['replies', replyToId] });
        queryClient.invalidateQueries({ queryKey: ['post', replyToId] });
      }
      onPosted?.(post);
    },
  });

  if (!me) return null;
  const length = text.trim().length;
  const remaining = MAX - text.length;

  function submit(e: FormEvent) {
    e.preventDefault();
    if (length > 0 && remaining >= 0 && !mutation.isPending) mutation.mutate();
  }

  return (
    <form className="composer" onSubmit={submit}>
      <Avatar user={me} size={48} />
      <div className="grow">
        <textarea
          ref={area}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) submit(e);
          }}
          placeholder={placeholder}
          aria-label={placeholder}
          autoFocus={autoFocus}
          rows={1}
        />
        {mutation.isError && <div className="error">{(mutation.error as Error).message}</div>}
        <div className="composer-footer">
          {text.length > 0 && <CharacterRing remaining={remaining} />}
          <button className="btn btn-primary" disabled={length === 0 || remaining < 0 || mutation.isPending}>
            {replyToId ? 'Reply' : 'Post'}
          </button>
        </div>
      </div>
    </form>
  );
}

/** A progress ring that turns amber near the limit and red past it, with the count shown at the end. */
function CharacterRing({ remaining }: { remaining: number }) {
  const r = 9;
  const circumference = 2 * Math.PI * r;
  const used = Math.min(1, (MAX - remaining) / MAX);
  const color = remaining < 0 ? 'var(--danger)' : remaining <= 20 ? '#ffad1f' : 'var(--accent)';
  return (
    <div className="row" aria-label={`${remaining} characters remaining`}>
      {remaining <= 20 && <span className={`ring-count ${remaining < 0 ? 'over' : ''}`}>{remaining}</span>}
      <svg className="ring" viewBox="0 0 22 22" aria-hidden="true">
        <circle cx="11" cy="11" r={r} fill="none" stroke="var(--border)" strokeWidth="2" />
        <circle
          cx="11"
          cy="11"
          r={r}
          fill="none"
          stroke={color}
          strokeWidth="2"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - used)}
          strokeLinecap="round"
        />
      </svg>
    </div>
  );
}
