import { Link } from 'react-router-dom';
import { linkify } from '../lib/linkify';

/** Post text with #hashtags (to search), @mentions (to profiles) and external links. */
export function RichText({ text }: { text: string }) {
  return (
    <>
      {linkify(text).map((segment, i) => {
        const stop = (e: React.MouseEvent) => e.stopPropagation();
        switch (segment.kind) {
          case 'hashtag':
            return (
              <Link key={i} to={`/explore?q=${encodeURIComponent(segment.tag)}`} onClick={stop}>
                {segment.text}
              </Link>
            );
          case 'mention':
            return (
              <Link key={i} to={`/u/${segment.username}`} onClick={stop}>
                {segment.text}
              </Link>
            );
          case 'url':
            return (
              <a key={i} href={segment.href} target="_blank" rel="noopener noreferrer nofollow" onClick={stop}>
                {segment.text.replace(/^https?:\/\//, '')}
              </a>
            );
          default:
            return <span key={i}>{segment.text}</span>;
        }
      })}
    </>
  );
}
