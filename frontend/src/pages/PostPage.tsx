import { useNavigate, useParams } from 'react-router-dom';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, PostView } from '../api/types';
import { Composer } from '../components/Composer';
import { BackIcon } from '../components/Icons';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard } from '../components/PostCard';
import { Empty, ErrorNote, Spinner } from '../components/Status';

export function PostPage() {
  const { id } = useParams();
  const postId = Number(id);
  const navigate = useNavigate();

  const post = useQuery({ queryKey: ['post', postId], queryFn: () => api.post(postId), enabled: Number.isFinite(postId) });
  const replies = useInfiniteQuery({
    queryKey: ['replies', postId],
    queryFn: ({ pageParam }) => api.replies(postId, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    enabled: post.isSuccess,
  });

  return (
    <>
      <header className="page-header">
        <button className="btn btn-small" onClick={() => navigate(-1)} aria-label="Back">
          <BackIcon width={18} height={18} />
        </button>
        <h1>Post</h1>
      </header>
      {post.isPending ? (
        <Spinner />
      ) : post.error ? (
        <ErrorNote error={post.error} />
      ) : (
        <div className="stack">
          <PostCard post={post.data} surface="POST" />
          <Composer replyToId={post.data.repostOf?.id ?? post.data.id} placeholder="Post your reply" />
          <InfiniteList<PostView, CursorPage<PostView>>
            query={replies}
            empty={<Empty title="No replies yet">Start the conversation.</Empty>}
            render={(reply, index) => <PostCard key={reply.id} post={reply} surface="POST" position={index} />}
          />
        </div>
      )}
    </>
  );
}
