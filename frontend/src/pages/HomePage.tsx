import { useState } from 'react';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, ForYouItem, ForYouPage, PostView } from '../api/types';
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
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>(initialTab);
  const [explain, setExplain] = useState(false);

  function choose(next: Tab) {
    if (next === tab) {
      // Tapping the active tab refreshes it and scrolls to the top, a familiar timeline gesture.
      window.scrollTo({ top: 0, behavior: 'smooth' });
      queryClient.resetQueries({ queryKey: ['timeline', next] });
      return;
    }
    setTab(next);
    try {
      localStorage.setItem(TAB_KEY, next);
    } catch {
      // Not remembered; fine.
    }
  }

  return (
    <>
      <div className="column-header">
        <div className="column-title">
          <h1 className="grow">Home</h1>
          {tab === 'for-you' && (
            <label className="row small muted" title="Show the features and score behind each recommendation">
              <input type="checkbox" checked={explain} onChange={(e) => setExplain(e.target.checked)} />
              Ranking signals
            </label>
          )}
        </div>
        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={tab === 'for-you'} className={`tab ${tab === 'for-you' ? 'active' : ''}`} onClick={() => choose('for-you')}>
            <span>For you</span>
          </button>
          <button role="tab" aria-selected={tab === 'following'} className={`tab ${tab === 'following' ? 'active' : ''}`} onClick={() => choose('following')}>
            <span>Following</span>
          </button>
        </div>
      </div>
      <Composer />
      {tab === 'for-you' ? <ForYouFeed explain={explain} /> : <FollowingFeed />}
    </>
  );
}

function ForYouFeed({ explain }: { explain: boolean }) {
  const query = useInfiniteQuery({
    queryKey: ['timeline', 'for-you', explain],
    queryFn: ({ pageParam }) => api.forYou(pageParam, explain),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    staleTime: 60_000,
  });

  return (
    <InfiniteList<ForYouItem, ForYouPage>
      query={query}
      empty={
        <Empty title="You're all caught up">
          You've seen everything recent. Follow a few people from Who to follow, or check back later.
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
    <InfiniteList<PostView, CursorPage<PostView>>
      query={query}
      empty={
        <Empty title="Welcome to your timeline">
          Posts from you and the people you follow show up here, newest first.
        </Empty>
      }
      render={(post, index) => <PostCard key={post.id} post={post} surface="FOLLOWING" position={index} />}
    />
  );
}
