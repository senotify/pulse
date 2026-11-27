import { describe, expect, it } from 'vitest';
import { actionText, actorLabel, notificationHref } from './notifications';

const actor = { id: 1, username: 'ana', displayName: 'Ana', avatarKey: null };

describe('notifications helpers', () => {
  it('labels grouped actors', () => {
    expect(actorLabel({ actor, actorCount: 1 })).toBe('Ana');
    expect(actorLabel({ actor, actorCount: 2 })).toBe('Ana and 1 other');
    expect(actorLabel({ actor, actorCount: 5 })).toBe('Ana and 4 others');
  });
  it('describes each type', () => {
    expect(actionText('LIKE')).toBe('liked your post');
    expect(actionText('FOLLOW')).toBe('followed you');
  });
  it('links to the post, or the profile for follows', () => {
    expect(notificationHref({ actor, postId: 42 })).toBe('/p/42');
    expect(notificationHref({ actor, postId: null })).toBe('/u/ana');
  });
});
