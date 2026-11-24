import { describe, expect, it } from 'vitest';
import { avatarHue, compactCount, initials, timeAgo } from './format';

describe('timeAgo', () => {
  const now = new Date('2026-11-24T12:00:00Z');

  it('uses compact units', () => {
    expect(timeAgo('2026-11-24T11:59:50Z', now)).toBe('now');
    expect(timeAgo('2026-11-24T11:55:00Z', now)).toBe('5m');
    expect(timeAgo('2026-11-24T09:00:00Z', now)).toBe('3h');
    expect(timeAgo('2026-11-22T12:00:00Z', now)).toBe('2d');
  });

  it('never shows negative time for clock skew', () => {
    expect(timeAgo('2026-11-24T12:00:30Z', now)).toBe('now');
  });

  it('falls back to a date after a week', () => {
    expect(timeAgo('2026-11-01T12:00:00Z', now)).not.toMatch(/^\d+[mhd]$/);
  });
});

describe('compactCount', () => {
  it('abbreviates thousands and millions', () => {
    expect(compactCount(999)).toBe('999');
    expect(compactCount(1000)).toBe('1K');
    expect(compactCount(1250)).toBe('1.3K');
    expect(compactCount(34_567)).toBe('34K');
    expect(compactCount(1_500_000)).toBe('1.5M');
  });
});

describe('initials and avatarHue', () => {
  it('takes first and last initials', () => {
    expect(initials('Arya Seno')).toBe('AS');
    expect(initials('alice')).toBe('AL');
    expect(initials('  ')).toBe('?');
  });

  it('gives a stable hue in range', () => {
    expect(avatarHue(7)).toBe(avatarHue(7));
    expect(avatarHue(7)).toBeGreaterThanOrEqual(0);
    expect(avatarHue(7)).toBeLessThan(360);
  });
});
