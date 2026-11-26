import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, PostView, UserResult } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard } from '../components/PostCard';
import { SearchBox, Trends, WhoToFollow } from '../components/RightSidebar';
import { Empty, ErrorNote, Spinner } from '../components/Status';

type Tab = 'posts' | 'people';

/**
 * Search results for ?q= with Posts and People tabs (?f=people), or trends and suggestions when
 * there is no query (also the discovery page on narrow screens).
 */
export function ExplorePage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q')?.trim() ?? '';
  const tab: Tab = params.get('f') === 'people' ? 'people' : 'posts';

  function choose(next: Tab) {
    const p = new URLSearchParams(params);
    if (next === 'people') p.set('f', 'people');
    else p.delete('f');
    setParams(p, { replace: true });
  }

  return (
    <>
      <div className="column-header">
        <div className="column-title" style={{ padding: '6px 16px' }}>
          <div className="grow">
            <SearchBox key={q} initial={q} />
          </div>
        </div>
        {q.length >= 1 && (
          <div className="tabs" role="tablist">
            <button role="tab" aria-selected={tab === 'posts'} className={`tab ${tab === 'posts' ? 'active' : ''}`} onClick={() => choose('posts')}>
              <span>Posts</span>
            </button>
            <button role="tab" aria-selected={tab === 'people'} className={`tab ${tab === 'people' ? 'active' : ''}`} onClick={() => choose('people')}>
              <span>People</span>
            </button>
          </div>
        )}
      </div>
      {q.length === 0 ? (
        <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Trends />
          <WhoToFollow />
        </div>
      ) : tab === 'people' ? (
        <People q={q} />
      ) : q.length >= 2 ? (
        <Posts q={q} />
      ) : (
        <Empty title="Keep typing">Post search needs at least 2 characters.</Empty>
      )}
    </>
  );
}

function Posts({ q }: { q: string }) {
  const query = useInfiniteQuery({
    queryKey: ['search', q],
    queryFn: ({ pageParam }) => api.search(q, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  return (
    <InfiniteList<PostView, CursorPage<PostView>>
      query={query}
      empty={<Empty title={`No results for "${q}"`}>Try searching for something else, or check People.</Empty>}
      render={(post, index) => <PostCard key={post.id} post={post} surface="POST" position={index} />}
    />
  );
}

function People({ q }: { q: string }) {
  const people = useQuery({ queryKey: ['search-users', q, 'full'], queryFn: () => api.searchUsers(q, 20) });
  if (people.isPending) return <Spinner />;
  if (people.error) return <ErrorNote error={people.error} />;
  if (people.data.length === 0) return <Empty title={`No people match "${q}"`}>Try a name or an @handle.</Empty>;
  return (
    <div>
      {people.data.map((user) => (
        <PersonRow key={user.id} user={user} />
      ))}
    </div>
  );
}

function PersonRow({ user }: { user: UserResult }) {
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const [following, setFollowing] = useState(user.followedByMe);
  const follow = useMutation({
    mutationFn: () => api.setEdge(user.username, 'follow', !following),
    onSuccess: () => {
      setFollowing((f: boolean) => !f);
      queryClient.invalidateQueries({ queryKey: ['timeline'] });
      queryClient.invalidateQueries({ queryKey: ['profile', user.username] });
    },
  });

  return (
    <Link to={`/u/${user.username}`} className="user-row">
      <Avatar user={user} size={48} />
      <div className="grow">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <div style={{ minWidth: 0 }}>
            <div className="bold truncate">{user.displayName}</div>
            <div className="muted truncate">@{user.username}</div>
          </div>
          {me && me.id !== user.id && (
            <button
              className={`btn btn-small ${following ? 'btn-outline btn-following' : ''}`}
              disabled={follow.isPending}
              onClick={(e) => {
                e.preventDefault();
                follow.mutate();
              }}
            >
              {following ? (
                <>
                  <span className="off-hover">Following</span>
                  <span className="on-hover">Unfollow</span>
                </>
              ) : (
                'Follow'
              )}
            </button>
          )}
        </div>
        {user.bio && <div style={{ marginTop: 4 }}>{user.bio}</div>}
      </div>
    </Link>
  );
}
