import { api, getToken } from './client';
import type { InteractionEvent } from './types';

const FLUSH_INTERVAL_MS = 4000;
const MAX_BATCH = 100;

/**
 * Batches engagement events for the recommender. Impressions are deduplicated per page load, so
 * scrolling back and forth does not inflate them. Failures are dropped: analytics must never
 * break the feed.
 */
class InteractionBatcher {
  private queue: InteractionEvent[] = [];
  private seen = new Set<string>();
  private timer: ReturnType<typeof setTimeout> | null = null;

  track(event: InteractionEvent) {
    if (!getToken()) return;
    if (event.type === 'IMPRESSION') {
      const key = `${event.surface}:${event.postId}`;
      if (this.seen.has(key)) return;
      this.seen.add(key);
    }
    this.queue.push(event);
    if (this.queue.length >= MAX_BATCH) this.flush();
    else if (!this.timer) this.timer = setTimeout(() => this.flush(), FLUSH_INTERVAL_MS);
  }

  flush() {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    while (this.queue.length > 0) {
      const batch = this.queue.splice(0, MAX_BATCH);
      api.interactions(batch).catch(() => {});
    }
  }

  /** Called on logout so one account's impressions never leak into the next. */
  reset() {
    this.queue = [];
    this.seen.clear();
  }
}

export const interactions = new InteractionBatcher();

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') interactions.flush();
  });
}
