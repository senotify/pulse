import { avatarHue, initials } from '../lib/format';
import type { UserSummary } from '../api/types';

export function Avatar({ user, size = 40 }: { user: UserSummary; size?: number }) {
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
