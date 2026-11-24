// Mirrors the Spring Boot API's JSON shapes.

export interface UserSummary {
  id: number;
  username: string;
  displayName: string;
  /** Null without a photo; otherwise the image is served at /api/avatars/{id}?v={avatarKey}. */
  avatarKey: string | null;
}

export interface Me extends UserSummary {
  email: string;
  bio: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: Me;
}

export interface UserProfile extends UserSummary {
  bio: string;
  createdAt: string;
  followers: number;
  following: number;
  posts: number;
  followedByMe: boolean;
  followsMe: boolean;
  blockedByMe: boolean;
  mutedByMe: boolean;
}

export interface PostView {
  id: number;
  author: UserSummary;
  content: string | null;
  replyToId: number | null;
  /** Author of the parent post, for "Replying to @user". */
  replyTo: UserSummary | null;
  repostOf: PostView | null;
  likeCount: number;
  replyCount: number;
  repostCount: number;
  likedByMe: boolean;
  repostedByMe: boolean;
  createdAt: string;
}

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

export type CandidateSource = 'IN_NETWORK' | 'ENGAGED_BY_FOLLOWING' | 'FOLLOWED_BY_FOLLOWING' | 'TRENDING';

export interface Features {
  inNetwork: number;
  authorAffinity: number;
  socialProof: number;
  engagementVelocity: number;
  recency: number;
  authorReputation: number;
  contentAffinity: number;
}

export interface ForYouItem {
  post: PostView;
  source: CandidateSource;
  reason: string;
  score: number;
  features?: Features;
}

export interface ForYouPage extends CursorPage<ForYouItem> {
  modelVersion: string;
}

export interface Trend {
  tag: string;
  posts: number;
}

export interface Suggestion extends UserSummary {
  mutuals: number;
  followers: number;
}

export type Surface ='FOR_YOU' | 'FOLLOWING' | 'PROFILE' | 'POST';

export interface InteractionEvent {
  postId: number;
  type: 'IMPRESSION' | 'CLICK' | 'DWELL';
  surface?: Surface;
  position?: number;
  dwellMs?: number;
  modelVersion?: string;
}
