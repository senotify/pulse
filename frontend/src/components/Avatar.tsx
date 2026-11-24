import { useState } from 'react';
import { avatarHue, initials } from '../lib/format';
import type { UserSummary } from '../api/types';

/** The profile photo, or coloured initials when there is none (or it fails to load). */
export function Avatar({ user, size = 40 }: { user: UserSummary; size?: number }) {
  const [broken, setBroken] = useState<string | null>(null);
  const src = user.avatarKey ? `/api/avatars/${user.id}?v=${user.avatarKey}` : null;

  if (src && broken !== src) {
    return (
      <img
        className="avatar"
        src={src}
        width={size}
        height={size}
        alt=""
        loading="lazy"
        decoding="async"
        onError={() => setBroken(src)}
        style={{ width: size, height: size, objectFit: 'cover' }}
      />
    );
  }

  const hue = avatarHue(user.id);
  return (
    <span
      className="avatar"
      style={{
        width: size,
        height: size,
        fontSize: size * 0.38,
        background: `linear-gradient(135deg, hsl(${hue} 62% 52%), hsl(${(hue + 40) % 360} 62% 42%))`,
      }}
      aria-hidden="true"
    >
      {initials(user.displayName || user.username)}
    </span>
  );
}
