import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, Me, PostView, ProfileFilter, UserProfile, UserSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Avatar } from '../components/Avatar';
import { BackIcon, CalendarIcon, CameraIcon } from '../components/Icons';
import { InfiniteList } from '../components/InfiniteList';
import { PostCard } from '../components/PostCard';
import { RichText } from '../components/RichText';
import { Empty, ErrorNote, Spinner } from '../components/Status';
import { avatarHue, compactCount } from '../lib/format';
import { squareJpeg } from '../lib/image';

/** The three tabs, plus the follower lists that open from the counts. */
type Tab = ProfileFilter | 'likes' | 'followers' | 'following';

const TABS: { id: Tab; label: string }[] = [
  { id: 'posts', label: 'Posts' },
  { id: 'replies', label: 'Posts & replies' },
  { id: 'media', label: 'Media' },
  { id: 'likes', label: 'Likes' },
];

export function ProfilePage() {
  const { username = '' } = useParams();
  const [tab, setTab] = useState<Tab>('posts');
  const navigate = useNavigate();
  // A different profile starts on Posts again.
  useEffect(() => setTab('posts'), [username]);
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
            {TABS.map((t) => (
              <button key={t.id} role="tab" aria-selected={tab === t.id} className={`tab ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
                <span>{t.label}</span>
              </button>
            ))}
          </div>
          {tab === 'followers' || tab === 'following' ? (
            <>
              <div className="column-title" style={{ minHeight: 44, borderBottom: '1px solid var(--border)' }}>
                <h2 style={{ fontSize: 17, margin: 0 }}>{tab === 'followers' ? 'Followers' : 'Following'}</h2>
              </div>
              <UserList username={profile.data.username} kind={tab} />
            </>
          ) : tab === 'media' ? (
            <UserMedia username={profile.data.username} />
          ) : tab === 'likes' ? (
            <UserLikes username={profile.data.username} />
          ) : (
            <UserPosts username={profile.data.username} filter={tab} />
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
          <ProfileAvatar profile={profile} editable={isMe} />
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

/**
 * The large profile avatar. On your own profile it is a button that opens a file picker; the
 * photo is cropped and resized in the browser, uploaded, and every cached view is refreshed.
 */
function ProfileAvatar({ profile, editable }: { profile: UserProfile; editable: boolean }) {
  const { updateMe } = useAuth();
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const upload = useMutation({
    mutationFn: async (file: File) => api.uploadAvatar(await squareJpeg(file)),
    onSuccess: (me) => {
      setError(null);
      updateMe(me);
      // The new key changes every embedded avatar URL, so refetch everything that shows users.
      queryClient.invalidateQueries();
    },
    onError: (e) => setError(e instanceof Error ? e.message : 'Upload failed'),
  });

  if (!editable) {
    return (
      <div className="profile-avatar">
        <Avatar user={profile} size={134} />
      </div>
    );
  }

  return (
    <div>
      <button
        type="button"
        className="profile-avatar avatar-edit"
        onClick={() => input.current?.click()}
        disabled={upload.isPending}
        aria-label="Change profile photo"
        title="Change profile photo"
      >
        <Avatar user={profile} size={134} />
        <span className="avatar-edit-overlay">{upload.isPending ? <span className="spinner" style={{ margin: 0 }} /> : <CameraIcon />}</span>
      </button>
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        hidden
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = ''; // allow choosing the same file again
          if (file) upload.mutate(file);
        }}
      />
      {error && <div className="error" style={{ marginTop: 4 }}>{error}</div>}
    </div>
  );
}

function EditProfile({ profile, onDone }: { profile: UserProfile; onDone: (me: Me) => void }) {
  const queryClient = useQueryClient();
  const { updateMe } = useAuth();
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [bio, setBio] = useState(profile.bio);
  const save = useMutation({
    mutationFn: () => api.updateMe({ displayName: displayName.trim(), bio }),
    onSuccess: onDone,
  });
  const removePhoto = useMutation({
    mutationFn: api.removeAvatar,
    onSuccess: (me) => {
      updateMe(me);
      queryClient.invalidateQueries();
    },
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
      <div className="row">
        <button className="btn" disabled={save.isPending || !displayName.trim()}>
          Save
        </button>
        {profile.avatarKey && (
          <button type="button" className="btn btn-outline" disabled={removePhoto.isPending} onClick={() => removePhoto.mutate()}>
            Remove photo
          </button>
        )}
        <span className="muted small">Click your avatar to change the photo.</span>
      </div>
    </form>
  );
}

function UserPosts({ username, filter }: { username: string; filter: 'posts' | 'replies' }) {
  const query = useInfiniteQuery({
    queryKey: ['user-posts', username, filter],
    queryFn: ({ pageParam }) => api.userPosts(username, filter, pageParam),
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

/** The Likes tab: posts the user liked, most recently liked first. */
function UserLikes({ username }: { username: string }) {
  const query = useInfiniteQuery({
    queryKey: ['user-likes', username],
    queryFn: ({ pageParam }) => api.userLikes(username, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  return (
    <InfiniteList<PostView, CursorPage<PostView>>
      query={query}
      empty={<Empty title={`@${username} hasn't liked anything`}>Posts they like will show up here.</Empty>}
      render={(post, index) => <PostCard key={post.id} post={post} surface="PROFILE" position={index} />}
    />
  );
}

/** The Media tab: a grid of the user's photos; each tile opens its post. */
function UserMedia({ username }: { username: string }) {
  const query = useInfiniteQuery({
    queryKey: ['user-posts', username, 'media'],
    queryFn: ({ pageParam }) => api.userPosts(username, 'media', pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  const sentinel = useRef<HTMLDivElement>(null);
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = query;
  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasNextPage) return;
    const observer = new IntersectionObserver(([entry]) => entry.isIntersecting && !isFetchingNextPage && fetchNextPage(), {
      rootMargin: '600px',
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (query.isPending) return <Spinner />;
  if (query.error) return <ErrorNote error={query.error} />;
  const tiles = query.data.pages.flatMap((page) => page.items.flatMap((post) => post.media.map((m) => ({ post, m }))));
  if (tiles.length === 0) {
    return <Empty title={`@${username} hasn't posted photos`}>When they do, they'll show up here.</Empty>;
  }
  return (
    <>
      <div className="media-tab">
        {tiles.map(({ post, m }) => (
          <Link key={m.id} to={`/p/${post.id}`} title={post.content ?? ''}>
            <img src={`/api/media/${m.id}`} alt="" loading="lazy" decoding="async" />
          </Link>
        ))}
      </div>
      <div ref={sentinel} />
      {isFetchingNextPage && <Spinner />}
    </>
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
