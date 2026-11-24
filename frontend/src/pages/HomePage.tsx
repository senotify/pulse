import { useState } from 'react';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { ForYouItem, ForYouPage, PostView } from '../api/types';
import { Composer } from '../components/Composer';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard } from '../components/PostCard';
import { Empty } from '../components/Status';

type Tab = 'for-you' | 'following';

const TAB_KEY = 'pulse.homeTab';

function initialTab(): Tab {
  try {
    return localStorage.getItem(TAB_KEY) === 'following' ? 'following' : 'for-you';
  } catch {
    return 'for-you';
  }
}

export function HomePage() {
  const [tab, setTab] = useState<Tab>(initialTab);
  const [explain, setExplain] = useState(false);

  function choose(next: Tab) {
    setTab(next);
    try {
      localStorage.setItem(TAB_KEY, next);
    } catch {
      // Not remembered; fine.
    }
  }

  return (
    <>
      <header className="page-header">
        <h1 className="grow">Home</h1>
        {tab === 'for-you' && (
          <label className="switch" title="Show the features and score behind each recommendation">
            <input type="checkbox" checked={explain} onChange={(e) => setExplain(e.target.checked)} />
            Ranking signals
          </label>
        )}
      </header>
      <Composer />
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'for-you'} className={`tab ${tab === 'for-you' ? 'active' : ''}`} onClick={() => choose('for-you')}>
          For you
        </button>
        <button role="tab" aria-selected={tab === 'following'} className={`tab ${tab === 'following' ? 'active' : ''}`} onClick={() => choose('following')}>
          Following
        </button>
      </div>
      {tab === 'for-you' ? <ForYouFeed explain={explain} /> : <FollowingFeed />}
    </>
  );
}

function ForYouFeed({ explain }: { explain: boolean }) {
  const queryClient = useQueryClient();
  const query = useInfiniteQuery({
    queryKey: ['timeline', 'for-you', explain],
    queryFn: ({ pageParam }) => api.forYou(pageParam, explain),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    staleTime: 60_000,
  });

  return (
    <>
      <div className="row" style={{ justifyContent: 'flex-end', marginBottom: 8 }}>
        <button
          className="btn btn-small btn-outline"
          onClick={() => queryClient.resetQueries({ queryKey: ['timeline', 'for-you'] })}
        >
          Refresh
        </button>
      </div>
      <InfiniteList<ForYouItem, ForYouPage>
        query={query}
        empty={
          <Empty title="Nothing new for you">
            You've seen everything recent. Follow a few people or check back later.
          </Empty>
        }
        render={(item, index, page) => (
          <PostCard
            key={item.post.id}
            post={item.post}
            surface="FOR_YOU"
            position={index}
            modelVersion={page.modelVersion}
            reason={item.reason}
            features={item.features}
            score={item.score}
          />
        )}
      />
    </>
  );
}

function FollowingFeed() {
  const query = useInfiniteQuery({
    queryKey: ['timeline', 'following'],
    queryFn: ({ pageParam }) => api.followingTimeline(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });

  return (
    <InfiniteList<PostView, { items: PostView[]; nextCursor: string | null }>
      query={query}
      empty={<Empty title="Your timeline is empty">Posts from you and people you follow show up here.</Empty>}
      render={(post, index) => <PostCard key={post.id} post={post} surface="FOLLOWING" position={index} />}
    />
  );
}
