import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { PostView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from './Avatar';

const MAX = 280;

export function Composer({
  replyToId,
  placeholder = "What's happening?",
  onPosted,
}: {
  replyToId?: number;
  placeholder?: string;
  onPosted?: (post: PostView) => void;
}) {
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const [text, setText] = useState('');

  const mutation = useMutation({
    mutationFn: () => api.createPost(text.trim(), replyToId),
    onSuccess: (post) => {
      setText('');
      queryClient.invalidateQueries({ queryKey: ['timeline'] });
      queryClient.invalidateQueries({ queryKey: ['user-posts', post.author.username] });
      queryClient.invalidateQueries({ queryKey: ['profile', post.author.username] });
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
    <form className="card composer" onSubmit={submit}>
      <Avatar user={me} />
      <div className="grow">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) submit(e);
          }}
          placeholder={placeholder}
          aria-label={placeholder}
          rows={2}
        />
        {mutation.isError && <div className="error">{(mutation.error as Error).message}</div>}
        <div className="composer-footer">
          <span className={`counter ${remaining < 0 ? 'over' : remaining <= 20 ? 'warn' : ''}`}>{remaining}</span>
          <button className="btn btn-primary" disabled={length === 0 || remaining < 0 || mutation.isPending}>
            {replyToId ? 'Reply' : 'Post'}
          </button>
        </div>
      </div>
    </form>
  );
}
