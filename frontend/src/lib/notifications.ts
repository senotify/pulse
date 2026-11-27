import type { NotificationView } from '../api/types';

/** "Ana", "Ana and Ben", or "Ana and 3 others" for grouped notifications. */
export function actorLabel(n: Pick<NotificationView, 'actor' | 'actorCount'>): string {
  const name = n.actor.displayName;
  if (n.actorCount <= 1) return name;
  const others = n.actorCount - 1;
  return others === 1 ? `${name} and 1 other` : `${name} and ${others} others`;
}

const VERBS: Record<NotificationView['type'], string> = {
  LIKE: 'liked your post',
  REPOST: 'reposted your post',
  REPLY: 'replied to your post',
  FOLLOW: 'followed you',
  MENTION: 'mentioned you',
};

export function actionText(type: NotificationView['type']): string {
  return VERBS[type] ?? 'interacted with you';
}

/** Where a notification leads: the post it is about, else the actor's profile. */
export function notificationHref(n: Pick<NotificationView, 'postId' | 'actor'>): string {
  return n.postId != null ? `/p/${n.postId}` : `/u/${n.actor.username}`;
}
