import { useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, PostView } from '../api/types';
import { Composer } from '../components/Composer';
import { BackIcon } from '../components/Icons';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard, PostDetail } from '../components/PostCard';
import { ErrorNote, Spinner } from '../components/Status';

/** A conversation: the posts above (joined by a line), the focused post, a reply box, then replies. */
export function PostPage() {
  const { id } = useParams();
  const postId = Number(id);
  const navigate = useNavigate();
  const focused = useRef<HTMLDivElement>(null);

  const post = useQuery({ queryKey: ['post', postId], queryFn: () => api.post(postId), enabled: Number.isFinite(postId) });
  const target = post.data?.repostOf ?? post.data;

  const ancestors = useQuery({
    queryKey: ['ancestors', target?.id],
    queryFn: () => api.ancestors(target!.id),
    enabled: target?.replyToId != null,
  });
  const parents = target?.replyToId != null ? (ancestors.data ?? []) : [];

  const replies = useInfiniteQuery({
    queryKey: ['replies', target?.id],
    queryFn: ({ pageParam }) => api.replies(target!.id, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled: target !== undefined,
  });

  // With context above, keep the focused post in view rather than the top of the thread.
  useEffect(() => {
    if (parents.length > 0) focused.current?.scrollIntoView({ block: 'start' });
  }, [parents.length, postId]);

  function focusReply() {
    focused.current?.querySelector('textarea')?.focus();
  }

  return (
    <>
      <div className="column-header">
        <div className="column-title">
          <button className="icon-button" onClick={() => navigate(-1)} aria-label="Back">
            <BackIcon />
          </button>
          <h1>Post</h1>
        </div>
      </div>
      {post.isPending ? (
        <Spinner />
      ) : post.error ? (
        <ErrorNote error={post.error} />
      ) : (
        <>
          {target!.replyToId != null && ancestors.isPending && <Spinner />}
          {parents.map((parent) => (
            <PostCard key={parent.id} post={parent} surface="POST" threadParent />
          ))}
          <div ref={focused} style={{ scrollMarginTop: 54 }}>
            <PostDetail post={target!} onReply={focusReply} hasParent={parents.length > 0} />
            <Composer
              replyToId={target!.id}
              placeholder={`Reply to @${target!.author.username}`}
            />
          </div>
          <InfiniteList<PostView, CursorPage<PostView>>
            query={replies}
            empty={null}
            render={(reply, index) => <PostCard key={reply.id} post={reply} surface="POST" position={index} />}
          />
        </>
      )}
    </>
  );
}
