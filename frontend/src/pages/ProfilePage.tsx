import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, Me, PostView, UserProfile, UserSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { BackIcon, CalendarIcon } from '../components/Icons';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard } from '../components/PostCard';
import { RichText } from '../components/RichText';
import { Empty, ErrorNote, Spinner } from '../components/Status';
import { avatarHue, compactCount } from '../lib/format';

type Tab = 'posts' | 'followers' | 'following';

export function ProfilePage() {
  const { username = '' } = useParams();
  const [tab, setTab] = useState<Tab>('posts');
  const navigate = useNavigate();
  const profile = useQuery({ queryKey: ['profile', username], queryFn: () => api.profile(username) });

  return (
    <>
      <div className="column-header">
        <div className="column-title">
          <button className="icon-button" onClick={() => navigate(-1)} aria-label="Back">
            <BackIcon />
          </button>
          <div className="grow">
            <h1 className="truncate">{profile.data?.displayName ?? 'Profile'}</h1>
            {profile.data && <div className="sub">{compactCount(profile.data.posts)} posts</div>}
          </div>
        </div>
      </div>
      {profile.isPending ? (
        <Spinner />
      ) : profile.error ? (
        <ErrorNote error={profile.error} />
      ) : (
        <>
          <ProfileHeader profile={profile.data} onShow={setTab} />
          <div className="tabs" role="tablist" style={{ borderBottom: '1px solid var(--border)' }}>
            {(['posts', 'followers', 'following'] as Tab[]).map((t) => (
              <button key={t} role="tab" aria-selected={tab === t} className={`tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
                <span>{t[0].toUpperCase() + t.slice(1)}</span>
              </button>
            ))}
          </div>
          {tab === 'posts' ? (
            <UserPosts username={profile.data.username} />
          ) : (
            <UserList username={profile.data.username} kind={tab} />
          )}
        </>
      )}
    </>
  );
}

function ProfileHeader({ profile, onShow }: { profile: UserProfile; onShow: (tab: Tab) => void }) {
  const { me, updateMe } = useAuth();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [confirmBlock, setConfirmBlock] = useState(false);
  const isMe = me?.id === profile.id;
  const hue = avatarHue(profile.id);

  const edge = useMutation({
    mutationFn: ({ kind, on }: { kind: 'follow' | 'mute' | 'block'; on: boolean }) =>
      api.setEdge(profile.username, kind, on),
    onSuccess: () => {
      setConfirmBlock(false);
      queryClient.invalidateQueries({ queryKey: ['profile', profile.username] });
      queryClient.invalidateQueries({ queryKey: ['timeline'] });
      queryClient.invalidateQueries({ queryKey: ['followers', profile.username] });
      queryClient.invalidateQueries({ queryKey: ['suggestions'] });
    },
  });

  return (
    <section>
      <div
        className="banner"
        style={{ background: `linear-gradient(120deg, hsl(${hue} 55% 45%), hsl(${(hue + 50) % 360} 60% 60%))` }}
      />
      <div className="profile-head">
        <div className="profile-actions">
          <div className="profile-avatar">
            <Avatar user={profile} size={134} />
          </div>
          {isMe ? (
            <button className="btn btn-outline" onClick={() => setEditing((v) => !v)}>
              {editing ? 'Cancel' : 'Edit profile'}
            </button>
          ) : profile.blockedByMe ? (
            <button className="btn btn-danger" disabled={edge.isPending} onClick={() => edge.mutate({ kind: 'block', on: false })}>
              Unblock
            </button>
          ) : profile.followedByMe ? (
            <button
              className="btn btn-outline btn-following"
              disabled={edge.isPending}
              onClick={() => edge.mutate({ kind: 'follow', on: false })}
            >
              <span className="off-hover">Following</span>
              <span className="on-hover">Unfollow</span>
            </button>
          ) : (
            <button className="btn" disabled={edge.isPending} onClick={() => edge.mutate({ kind: 'follow', on: true })}>
              Follow
            </button>
          )}
        </div>

        <h2 className="profile-name">{profile.displayName}</h2>
        <div className="muted">
          @{profile.username}
          {profile.followsMe && <span className="badge">Follows you</span>}
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
          <>
            {profile.bio && (
              <p className="profile-bio">
                <RichText text={profile.bio} />
              </p>
            )}
            <div className="profile-facts">
              <span>
                <CalendarIcon />
                Joined {new Date(profile.createdAt).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}
              </span>
            </div>
            <div className="profile-counts">
              <button className="link-plain" onClick={() => onShow('following')}>
                <b>{compactCount(profile.following)}</b> Following
              </button>
              <button className="link-plain" onClick={() => onShow('followers')}>
                <b>{compactCount(profile.followers)}</b> Followers
              </button>
            </div>
          </>
        )}

        {!isMe && !profile.blockedByMe && (
          <div className="profile-tools">
            <button className="btn btn-small btn-outline" disabled={edge.isPending} onClick={() => edge.mutate({ kind: 'mute', on: !profile.mutedByMe })}>
              {profile.mutedByMe ? 'Unmute' : 'Mute'}
            </button>
            {confirmBlock ? (
              <>
                <button className="btn btn-small btn-danger" disabled={edge.isPending} onClick={() => edge.mutate({ kind: 'block', on: true })}>
                  Block @{profile.username}
                </button>
                <button className="btn btn-small btn-outline" onClick={() => setConfirmBlock(false)}>
                  Cancel
                </button>
                <span className="muted small">They won't see your posts, and you won't see theirs.</span>
              </>
            ) : (
              <button className="btn btn-small btn-outline" onClick={() => setConfirmBlock(true)}>
                Block
              </button>
            )}
            {edge.isError && <span className="error">{(edge.error as Error).message}</span>}
          </div>
        )}
      </div>
    </section>
  );
}

function EditProfile({ profile, onDone }: { profile: UserProfile; onDone: (me: Me) => void }) {
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [bio, setBio] = useState(profile.bio);
  const save = useMutation({
    mutationFn: () => api.updateMe({ displayName: displayName.trim(), bio }),
    onSuccess: onDone,
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    save.mutate();
  }

  return (
    <form className="edit-form" onSubmit={submit}>
      <label className="field">
        <span className="field-label">
          Name <span>{displayName.length} / 50</span>
        </span>
        <input className="input" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={50} required />
      </label>
      <label className="field">
        <span className="field-label">
          Bio <span>{bio.length} / 160</span>
        </span>
        <textarea className="input textarea" value={bio} onChange={(e) => setBio(e.target.value)} maxLength={160} />
      </label>
      {save.isError && <div className="error">{(save.error as Error).message}</div>}
      <div>
        <button className="btn" disabled={save.isPending || !displayName.trim()}>
          Save
        </button>
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
      empty={<Empty title={`@${username} hasn't posted`}>When they do, their posts will show up here.</Empty>}
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
      empty={
        <Empty title={kind === 'followers' ? 'No followers yet' : `@${username} isn't following anyone`} />
      }
      render={(user) => (
        <Link key={user.id} to={`/u/${user.username}`} className="user-row">
          <Avatar user={user} size={48} />
          <div className="grow">
            <div className="bold truncate">{user.displayName}</div>
            <div className="muted truncate">@{user.username}</div>
          </div>
        </Link>
      )}
    />
  );
}
