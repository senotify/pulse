import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { CursorPage, NotificationView } from '../api/types';
import { Avatar } from '../components/Avatar';
import { InfiniteList } from '../components/InfiniteList';
import { Empty } from '../components/Status';
import { timeAgo } from '../lib/format';
import { actionText, actorLabel, notificationHref } from '../lib/notifications';

/**
 * Notifications, newest first. Unread items stay highlighted while the page is open and are
 * marked read on the server as soon as they have been loaded (viewed).
 */
export function NotificationsPage() {
  const queryClient = useQueryClient();
  const sent = useRef(new Set<number>());
  const query = useInfiniteQuery({
    queryKey: ['notifications', 'list'],
    queryFn: ({ pageParam }) => api.notifications(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    const ids = (query.data?.pages ?? [])
      .flatMap((p) => p.items)
      .filter((n) => !n.read && !sent.current.has(n.id))
      .map((n) => n.id);
    if (ids.length === 0) return;
    ids.forEach((id) => sent.current.add(id));
    api
      .markNotificationsRead(ids)
      .then(() => queryClient.invalidateQueries({ queryKey: ['notifications', 'unread'] }))
      .catch(() => ids.forEach((id) => sent.current.delete(id)));
  }, [query.data, queryClient]);

  return (
    <>
      <div className="column-header">
        <div className="column-title">
          <h1 className="grow">Notifications</h1>
        </div>
      </div>
      <InfiniteList<NotificationView, CursorPage<NotificationView>>
        query={query}
        empty={<Empty title="Nothing yet">Likes, reposts, replies, follows and mentions show up here.</Empty>}
        render={(n) => (
          <Link key={n.id} to={notificationHref(n)} className={`notification ${n.read ? '' : 'unread'}`}>
            <Avatar user={n.actor} size={40} />
            <div className="grow">
              <div>
                <span className="bold">{actorLabel(n)}</span> {actionText(n.type)}{' '}
                <span className="muted">· {timeAgo(n.createdAt)}</span>
              </div>
              {n.postPreview && <div className="preview">{n.postPreview}</div>}
            </div>
          </Link>
        )}
      />
    </>
  );
}
