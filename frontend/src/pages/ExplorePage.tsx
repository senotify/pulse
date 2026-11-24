import { useSearchParams } from 'react-router-dom';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, PostView } from '../api/types';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard } from '../components/PostCard';
import { SearchBox, Trends, WhoToFollow } from '../components/RightSidebar';
import { Empty } from '../components/Status';

/** Search results for ?q=, or trends and suggestions when there is no query (also the mobile discovery page). */
export function ExplorePage() {
  const [params] = useSearchParams();
  const q = params.get('q')?.trim() ?? '';

  return (
    <>
      <div className="column-header">
        <div className="column-title" style={{ padding: '6px 16px' }}>
          <div className="grow">
            <SearchBox key={q} initial={q} />
          </div>
        </div>
      </div>
      {q.length >= 2 ? (
        <Results q={q} />
      ) : (
        <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Trends />
          <WhoToFollow />
        </div>
      )}
    </>
  );
}

function Results({ q }: { q: string }) {
  const query = useInfiniteQuery({
    queryKey: ['search', q],
    queryFn: ({ pageParam }) => api.search(q, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  return (
    <InfiniteList<PostView, CursorPage<PostView>>
      query={query}
      empty={
        <Empty title={`No results for "${q}"`}>Try searching for something else, or check the spelling.</Empty>
      }
      render={(post, index) => <PostCard key={post.id} post={post} surface="POST" position={index} />}
    />
  );
}
