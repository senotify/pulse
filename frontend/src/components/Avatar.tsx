import { useState } from 'react';
import { avatarHue } from '../lib/format';
import type { UserSummary } from '../api/types';

/** The profile photo, or the default egg on a per-user colour when there is none (or it fails to load). */
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

  return <DefaultEgg hue={avatarHue(user.id)} size={size} />;
}

/** A plain egg, slightly tilted, on a colour derived from the user id so people stay distinguishable. */
function DefaultEgg({ hue, size }: { hue: number; size: number }) {
  return (
    <svg className="avatar" width={size} height={size} viewBox="0 0 100 100" aria-hidden="true" style={{ width: size, height: size }}>
      <circle cx="50" cy="50" r="50" fill={`hsl(${hue} 55% 55%)`} />
      <path
        d="M50 20c-15 0-25 22-25 38 0 15 11 24 25 24s25-9 25-24c0-16-10-38-25-38z"
        fill="#fff"
        opacity="0.92"
        transform="rotate(-12 50 52)"
      />
    </svg>
  );
}
