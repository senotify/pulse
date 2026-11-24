export type Segment =
  | { kind: 'text'; text: string }
  | { kind: 'hashtag'; text: string; tag: string }
  | { kind: 'mention'; text: string; username: string }
  | { kind: 'url'; text: string; href: string };

// Mentions follow the API's username rule; hashtags match the API's trend extractor.
const TOKEN =
  /(https?:\/\/[^\s<]+)|(?<![\p{L}\p{N}_@])@([A-Za-z0-9_]{3,20})(?![A-Za-z0-9_])|(?<![\p{L}\p{N}_&#])#([\p{L}\p{N}_]{2,50})/gu;
const TRAILING_PUNCTUATION = /[.,!?;:)\]'"]+$/;

/** Splits post text into plain text, #hashtags, @mentions and links, without losing a character. */
export function linkify(text: string): Segment[] {
  const segments: Segment[] = [];
  let last = 0;
  for (const match of text.matchAll(TOKEN)) {
    let raw = match[0];
    const start = match.index ?? 0;
    if (match[1]) {
      raw = raw.replace(TRAILING_PUNCTUATION, ''); // "see https://x.dev." keeps the period as text
    }
    if (start > last) segments.push({ kind: 'text', text: text.slice(last, start) });
    if (match[1]) segments.push({ kind: 'url', text: raw, href: raw });
    else if (match[2]) segments.push({ kind: 'mention', text: raw, username: match[2].toLowerCase() });
    else segments.push({ kind: 'hashtag', text: raw, tag: `#${match[3].toLowerCase()}` });
    last = start + raw.length;
  }
  if (last < text.length) segments.push({ kind: 'text', text: text.slice(last) });
  return segments;
}
