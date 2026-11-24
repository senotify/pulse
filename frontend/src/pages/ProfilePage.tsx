import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, PostView, UserProfile, UserSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { BackIcon } from '../components/Icons';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard } from '../components/PostCard';
import { Empty, ErrorNote, Spinner } from '../components/Status';
import { compactCount } from '../lib/format';

type Tab = 'posts' | 'followers' | 'following';

export function ProfilePage() {
  const { username = '' } = useParams();
  const [tab, setTab] = useState<Tab>('posts');
  const navigate = useNavigate();
  const profile = useQuery({ queryKey: ['profile', username], queryFn: () => api.profile(username) });

  return (
    <>
      <header className="page-header">
        <button className="btn btn-small" onClick={() => navigate(-1)} aria-label="Back">
          <BackIcon width={18} height={18} />
        </button>
        <h1 className="truncate">{profile.data?.displayName ?? `@${username}`}</h1>
      </header>
      {profile.isPending ? (
        <Spinner />
      ) : profile.error ? (
        <ErrorNote error={profile.error} />
      ) : (
        <>
          <ProfileHeader profile={profile.data} />
          <div className="tabs" role="tablist">
            {(['posts', 'followers', 'following'] as Tab[]).map((t) => (
              <button key={t} role="tab" aria-selected={tab === t} className={`tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
                {t[0].toUpperCase() + t.slice(1)}
              </button>
            ))}
          </div>
          {tab === 'posts' ? <UserPosts username={profile.data.username} /> : <UserList username={profile.data.username} kind={tab} />}
        </>
      )}
    </>
  );
}

function ProfileHeader({ profile }: { profile: UserProfile }) {
  const { me, updateMe } = useAuth();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [confirmBlock, setConfirmBlock] = useState(false);
  const isMe = me?.id === profile.id;

  const edge = useMutation({
    mutationFn: ({ kind, on }: { kind: 'follow' | 'mute' | 'block'; on: boolean }) =>
      api.setEdge(profile.username, kind, on),
    onSuccess: () => {
      setConfirmBlock(false);
      queryClient.invalidateQueries({ queryKey: ['profile', profile.username] });
      queryClient.invalidateQueries({ queryKey: ['timeline'] });
      queryClient.invalidateQueries({ queryKey: ['followers', profile.username] });
    },
  });

  return (
    <section className="card profile-card">
      <div className="profile-top">
        <Avatar user={profile} size={72} />
        <div className="grow">
          <h1 className="truncate">{profile.displayName}</h1>
          <div className="row faint">
            @{profile.username}
            {profile.followsMe && <span className="badge">Follows you</span>}
          </div>
        </div>
        {isMe ? (
          <button className="btn btn-outline" onClick={() => setEditing((v) => !v)}>
            {editing ? 'Cancel' : 'Edit profile'}
          </button>
        ) : (
          !profile.blockedByMe && (
            <button
              className={`btn ${profile.followedByMe ? 'btn-outline' : 'btn-primary'}`}
              disabled={edge.isPending}
              onClick={() => edge.mutate({ kind: 'follow', on: !profile.followedByMe })}
            >
              {profile.followedByMe ? 'Following' : 'Follow'}
            </button>
          )
        )}
      </div>

      {editing && isMe ? (
        <EditProfile
          profile={profile}
          onDone={(updated) => {
            updateMe(updated);
            setEditing(false);
            queryClient.invalidateQueries({ queryKey: ['profile', profile.username] });
          }}
        />
      ) : (
        profile.bio && <p className="profile-bio">{profile.bio}</p>
      )}

      <div className="stats" style={{ marginTop: profile.bio ? 0 : 12 }}>
        <span><b>{compactCount(profile.posts)}</b> posts</span>
        <span><b>{compactCount(profile.followers)}</b> followers</span>
        <span><b>{compactCount(profile.following)}</b> following</span>
        <span className="faint">Joined {new Date(profile.createdAt).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</span>
      </div>

      {!isMe && (
        <div className="row" style={{ marginTop: 14, flexWrap: 'wrap' }}>
          <button className="btn btn-small btn-outline" disabled={edge.isPending} onClick={() => edge.mutate({ kind: 'mute', on: !profile.mutedByMe })}>
            {profile.mutedByMe ? 'Unmute' : 'Mute'}
          </button>
          {profile.blockedByMe ? (
            <button className="btn btn-small btn-outline" disabled={edge.isPending} onClick={() => edge.mutate({ kind: 'block', on: false })}>
              Unblock
            </button>
          ) : confirmBlock ? (
            <>
              <button className="btn btn-small btn-danger" disabled={edge.isPending} onClick={() => edge.mutate({ kind: 'block', on: true })}>
                Confirm block
              </button>
              <button className="btn btn-small" onClick={() => setConfirmBlock(false)}>Cancel</button>
              <span className="faint small">Removes follows both ways and hides each other's posts.</span>
            </>
          ) : (
            <button className="btn btn-small btn-outline" onClick={() => setConfirmBlock(true)}>Block</button>
          )}
          {edge.isError && <span className="error">{(edge.error as Error).message}</span>}
        </div>
      )}
    </section>
  );
}

function EditProfile({ profile, onDone }: { profile: UserProfile; onDone: (me: Awaited<ReturnType<typeof api.updateMe>>) => void }) {
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [bio, setBio] = useState(profile.bio);
  const save = useMutation({ mutationFn: () => api.updateMe({ displayName: displayName.trim(), bio }), onSuccess: onDone });

  function submit(e: FormEvent) {
    e.preventDefault();
    save.mutate();
  }

  return (
    <form className="stack" style={{ marginTop: 14 }} onSubmit={submit}>
      <label className="label">
        Display name
        <input className="input" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={50} required />
      </label>
      <label className="label">
        Bio <span className="faint">{160 - bio.length}</span>
        <textarea className="textarea" value={bio} onChange={(e) => setBio(e.target.value)} maxLength={160} />
      </label>
      {save.isError && <div className="error">{(save.error as Error).message}</div>}
      <div>
        <button className="btn btn-primary" disabled={save.isPending || !displayName.trim()}>Save</button>
      </div>
    </form>
  );
}

function UserPosts({ username }: { username: string }) {
  const query = useInfiniteQuery({
    queryKey: ['user-posts', username],
    queryFn: ({ pageParam }) => api.userPosts(username, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  return (
    <InfiniteList<PostView, CursorPage<PostView>>
      query={query}
      empty={<Empty title="No posts yet" />}
      render={(post, index) => <PostCard key={post.id} post={post} surface="PROFILE" position={index} />}
    />
  );
}

function UserList({ username, kind }: { username: string; kind: 'followers' | 'following' }) {
  const query = useInfiniteQuery({
    queryKey: [kind, username],
    queryFn: ({ pageParam }) => (kind === 'followers' ? api.followers(username, pageParam) : api.following(username, pageParam)),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  return (
    <InfiniteList<UserSummary, CursorPage<UserSummary>>
      query={query}
      empty={<Empty title={kind === 'followers' ? 'No followers yet' : 'Not following anyone yet'} />}
      render={(user) => (
        <Link key={user.id} to={`/u/${user.username}`} className="card user-row">
          <Avatar user={user} />
          <div className="grow">
            <div className="bold truncate">{user.displayName}</div>
            <div className="faint small">@{user.username}</div>
          </div>
        </Link>
      )}
    />
  );
}
