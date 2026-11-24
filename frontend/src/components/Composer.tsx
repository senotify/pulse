import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { MediaView, PostView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { fittedJpeg } from '../lib/image';
import { Avatar } from './Avatar';
import { CloseIcon, ImageIcon } from './Icons';

const MAX = 280;
const MAX_PHOTOS = 4;

/** A photo picked in the composer. It uploads immediately, so it is usually ready before the text is. */
interface Attachment {
  key: string;
  preview: string;
  status: 'uploading' | 'ready' | 'error';
  media?: MediaView;
  error?: string;
}

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
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const area = useRef<HTMLTextAreaElement>(null);
  const picker = useRef<HTMLInputElement>(null);

  // Grow with the text instead of scrolling inside a tiny box.
  useEffect(() => {
    const el = area.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [text]);

  // Free preview object URLs when the composer goes away.
  const previews = useRef<string[]>([]);
  useEffect(() => () => previews.current.forEach((url) => URL.revokeObjectURL(url)), []);

  const update = (key: string, patch: Partial<Attachment>) =>
    setAttachments((list) => list.map((a) => (a.key === key ? { ...a, ...patch } : a)));

  function addFiles(files: FileList | null) {
    if (!files) return;
    const room = MAX_PHOTOS - attachments.length;
    for (const file of Array.from(files).slice(0, room)) {
      const key = `${Date.now()}-${Math.random()}`;
      const preview = URL.createObjectURL(file);
      previews.current.push(preview);
      setAttachments((list) => [...list, { key, preview, status: 'uploading' }]);
      fittedJpeg(file)
        .then((blob) => api.uploadMedia(blob))
        .then((media) => update(key, { status: 'ready', media }))
        .catch((e: unknown) => update(key, { status: 'error', error: e instanceof Error ? e.message : 'Upload failed' }));
    }
  }

  function remove(key: string) {
    setAttachments((list) => list.filter((a) => a.key !== key));
  }

  const mutation = useMutation({
    mutationFn: () =>
      api.createPost(
        text.trim(),
        replyToId,
        attachments.flatMap((a) => (a.media ? [a.media.id] : [])),
      ),
    onSuccess: (post) => {
      setText('');
      setAttachments([]);
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
  const uploading = attachments.some((a) => a.status === 'uploading');
  const ready = attachments.filter((a) => a.status === 'ready').length;
  const canPost = (length > 0 || ready > 0) && remaining >= 0 && !uploading && !mutation.isPending;

  function submit(e: FormEvent) {
    e.preventDefault();
    if (canPost) mutation.mutate();
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
          onPaste={(e) => {
            if (e.clipboardData.files.length > 0) addFiles(e.clipboardData.files);
          }}
          placeholder={placeholder}
          aria-label={placeholder}
          autoFocus={autoFocus}
          rows={1}
        />
        {attachments.length > 0 && (
          <div className="attachments">
            {attachments.map((a) => (
              <div key={a.key} className={`attachment ${a.status}`}>
                <img src={a.preview} alt="" />
                <button type="button" className="remove" onClick={() => remove(a.key)} aria-label="Remove photo">
                  <CloseIcon />
                </button>
                {a.status === 'error' && <span className="badge-error">{a.error}</span>}
              </div>
            ))}
          </div>
        )}
        {mutation.isError && <div className="error">{(mutation.error as Error).message}</div>}
        <div className="composer-footer">
          <div className="composer-tools">
            <button
              type="button"
              className="icon-button"
              onClick={() => picker.current?.click()}
              disabled={attachments.length >= MAX_PHOTOS}
              aria-label="Add photos"
              title={`Add up to ${MAX_PHOTOS} photos`}
            >
              <ImageIcon />
            </button>
            <input
              ref={picker}
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              hidden
              onChange={(e) => {
                addFiles(e.target.files);
                e.target.value = '';
              }}
            />
          </div>
          {text.length > 0 && <CharacterRing remaining={remaining} />}
          <button className="btn btn-primary" disabled={!canPost}>
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
