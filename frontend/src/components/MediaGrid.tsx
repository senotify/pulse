import type { MediaView } from '../api/types';

/**
 * Photos in a post, laid out the familiar way: one photo keeps its shape, two sit side by side,
 * three are one tall plus two stacked, four form a 2x2 grid. Clicking opens the full image.
 */
export function MediaGrid({ media }: { media: MediaView[] }) {
  if (media.length === 0) return null;
  const single = media.length === 1 ? media[0] : null;

  return (
    <div
      className={`media-grid count-${media.length}`}
      style={single ? { aspectRatio: `${single.width} / ${Math.max(single.height, single.width * 0.5)}` } : undefined}
      onClick={(e) => e.stopPropagation()}
    >
      {media.map((m, i) => (
        <a key={m.id} href={`/api/media/${m.id}`} target="_blank" rel="noopener noreferrer" className={`media-cell cell-${i}`}>
          <img src={`/api/media/${m.id}`} alt="" loading="lazy" decoding="async" width={m.width} height={m.height} />
        </a>
      ))}
    </div>
  );
}
