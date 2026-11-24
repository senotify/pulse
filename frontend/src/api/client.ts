import type {
  AuthResponse,
  CursorPage,
  ForYouPage,
  InteractionEvent,
  Me,
  MediaView,
  PostView,
  ProfileFilter,
  Suggestion,
  Trend,
  UserProfile,
  UserSummary,
} from './types';

const TOKEN_KEY = 'pulse.token';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

let onUnauthorized: () => void = () => {};

/** Called once by the auth provider so an expired token signs the user out everywhere. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage unavailable (private mode): the session lasts for this tab only.
  }
}

async function request<T>(method: string, path: string, body?: unknown, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  const form = body instanceof FormData;
  // FormData sets its own multipart boundary header; everything else is JSON.
  if (body !== undefined && !form) headers['Content-Type'] = 'application/json';

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : form ? (body as FormData) : JSON.stringify(body),
    ...init,
  });

  if (res.status === 401 && token) onUnauthorized();
  if (!res.ok) {
    let message = res.statusText || 'Request failed';
    try {
      const problem = await res.json();
      message = problem.detail ?? fieldErrors(problem) ?? problem.title ?? message;
    } catch {
      // Not a problem-details body.
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204 || res.status === 202) return undefined as T;
  return res.json() as Promise<T>;
}

function fieldErrors(problem: { errors?: { field: string; defaultMessage: string }[] }): string | undefined {
  return problem.errors?.map((e) => `${e.field} ${e.defaultMessage}`).join(', ');
}

function qs(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== false) search.set(key, String(value));
  }
  const s = search.toString();
  return s ? `?${s}` : '';
}

export const api = {
  register: (body: { username: string; email: string; displayName: string; password: string }) =>
    request<AuthResponse>('POST', '/auth/register', body),
  login: (body: { login: string; password: string }) => request<AuthResponse>('POST', '/auth/login', body),
  me: () => request<Me>('GET', '/me'),
  updateMe: (body: { displayName?: string; bio?: string }) => request<Me>('PATCH', '/me', body),
  uploadAvatar: (image: Blob) => {
    const form = new FormData();
    form.append('file', image, 'avatar.jpg');
    return request<Me>('PUT', '/me/avatar', form);
  },
  removeAvatar: () => request<Me>('DELETE', '/me/avatar'),

  profile: (username: string) => request<UserProfile>('GET', `/users/${encodeURIComponent(username)}`),
  userPosts: (username: string, filter: ProfileFilter, cursor?: string | null) =>
    request<CursorPage<PostView>>('GET', `/users/${encodeURIComponent(username)}/posts${qs({ filter, cursor })}`),
  followers: (username: string, cursor?: string | null) =>
    request<CursorPage<UserSummary>>('GET', `/users/${encodeURIComponent(username)}/followers${qs({ cursor })}`),
  following: (username: string, cursor?: string | null) =>
    request<CursorPage<UserSummary>>('GET', `/users/${encodeURIComponent(username)}/following${qs({ cursor })}`),
  setEdge: (username: string, edge: 'follow' | 'mute' | 'block', on: boolean) =>
    request<void>(on ? 'PUT' : 'DELETE', `/users/${encodeURIComponent(username)}/${edge}`),

  createPost: (content: string, replyToId?: number, mediaIds: number[] = []) =>
    request<PostView>('POST', '/posts', { content, replyToId, mediaIds }),
  uploadMedia: (image: Blob) => {
    const form = new FormData();
    form.append('file', image, 'photo.jpg');
    return request<MediaView>('POST', '/media', form);
  },
  post: (id: number) => request<PostView>('GET', `/posts/${id}`),
  deletePost: (id: number) => request<void>('DELETE', `/posts/${id}`),
  ancestors: (id: number) => request<PostView[]>('GET', `/posts/${id}/ancestors`),
  replies: (id: number, cursor?: string | null) =>
    request<CursorPage<PostView>>('GET', `/posts/${id}/replies${qs({ cursor })}`),
  setLike: (id: number, on: boolean) => request<PostView>(on ? 'PUT' : 'DELETE', `/posts/${id}/like`),
  setRepost: (id: number, on: boolean) => request<PostView>(on ? 'PUT' : 'DELETE', `/posts/${id}/repost`),

  followingTimeline: (cursor?: string | null) =>
    request<CursorPage<PostView>>('GET', `/timeline/following${qs({ cursor })}`),
  forYou: (cursor: string | null | undefined, explain: boolean) =>
    request<ForYouPage>('GET', `/timeline/for-you${qs({ cursor, explain })}`),

  trends: () => request<Trend[]>('GET', '/discover/trends'),
  suggestions: (limit = 3) => request<Suggestion[]>('GET', `/discover/users${qs({ limit })}`),
  search: (q: string, cursor?: string | null) =>
    request<CursorPage<PostView>>('GET', `/search/posts${qs({ q, cursor })}`),

  /** keepalive lets a final batch survive the page being closed. */
  interactions: (events: InteractionEvent[]) =>
    request<void>('POST', '/interactions', { events }, { keepalive: true }),
};
