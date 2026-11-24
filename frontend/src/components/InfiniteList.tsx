import { useEffect, useRef, type ReactNode } from 'react';
import type { InfiniteData, UseInfiniteQueryResult } from '@tanstack/react-query';
import type { CursorPage } from '../api/types';
import { Empty, ErrorNote, Spinner } from './Status';

/**
 * Renders a cursor-paginated query and loads the next page when the end of the list scrolls
 * into view, with a button as the fallback.
 */
export function InfiniteList<T, P extends CursorPage<T>>({
  query,
  render,
  empty,
}: {
  query: UseInfiniteQueryResult<InfiniteData<P>>;
  render: (item: T, index: number, page: P) => ReactNode;
  empty: ReactNode;
}) {
  const sentinel = useRef<HTMLDivElement>(null);
  const { data, error, isPending, hasNextPage, isFetchingNextPage, fetchNextPage } = query;

  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasNextPage) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isFetchingNextPage) fetchNextPage();
      },
      { rootMargin: '400px' },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  if (isPending) return <Spinner />;
  if (error) return <ErrorNote error={error} />;

  let index = 0;
  const rows: ReactNode[] = [];
  for (const page of data.pages) {
    for (const item of page.items) {
      rows.push(render(item, index++, page));
    }
  }
  if (rows.length === 0) return <>{empty}</>;

  return (
    <div className="feed">
      {rows}
      <div ref={sentinel} />
      {isFetchingNextPage && <Spinner />}
      {hasNextPage && !isFetchingNextPage && (
        <button className="btn btn-outline" onClick={() => fetchNextPage()}>
          Load more
        </button>
      )}
      {!hasNextPage && index > 5 && <Empty title="You're all caught up" />}
    </div>
  );
}
