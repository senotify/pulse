import { useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, PostView } from '../api/types';
import { Composer } from '../components/Composer';
import { BackIcon } from '../components/Icons';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard, PostDetail } from '../components/PostCard';
import { ErrorNote, Spinner } from '../components/Status';

export function PostPage() {
  const { id } = useParams();
  const postId = Number(id);
  const navigate = useNavigate();
  const replyBox = useRef<HTMLDivElement>(null);

  const post = useQuery({ queryKey: ['post', postId], queryFn: () => api.post(postId), enabled: Number.isFinite(postId) });
  const target = post.data?.repostOf ?? post.data;
  const replies = useInfiniteQuery({
    queryKey: ['replies', target?.id],
    queryFn: ({ pageParam }) => api.replies(target!.id, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled: target !== undefined,
  });

  function focusReply() {
    replyBox.current?.querySelector('textarea')?.focus();
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
          <PostDetail post={target!} onReply={focusReply} />
          <div ref={replyBox}>
            <Composer replyToId={target!.id} placeholder="Post your reply" />
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
