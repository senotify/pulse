import { describe, expect, it } from 'vitest';
import { linkify } from './linkify';

const join = (text: string) =>
  linkify(text)
    .map((s) => s.text)
    .join('');

describe('linkify', () => {
  it('finds hashtags, mentions and links', () => {
    expect(linkify('hi @Nadia see #Java at https://pulse.dev/x')).toEqual([
      { kind: 'text', text: 'hi ' },
      { kind: 'mention', text: '@Nadia', username: 'nadia' },
      { kind: 'text', text: ' see ' },
      { kind: 'hashtag', text: '#Java', tag: '#java' },
      { kind: 'text', text: ' at ' },
      { kind: 'url', text: 'https://pulse.dev/x', href: 'https://pulse.dev/x' },
    ]);
  });

  it('leaves trailing punctuation out of links', () => {
    const segments = linkify('read https://pulse.dev/post.');
    expect(segments[1]).toEqual({ kind: 'url', text: 'https://pulse.dev/post', href: 'https://pulse.dev/post' });
    expect(segments[2]).toEqual({ kind: 'text', text: '.' });
  });

  it('ignores emails, mid-word hashes and too-short handles', () => {
    expect(linkify('mail me@pulse.dev, issue a#1, @ab, &#39;').every((s) => s.kind === 'text')).toBe(true);
  });

  it('supports non-Latin hashtags', () => {
    expect(linkify('#kopi ☕ #日本')).toContainEqual({ kind: 'hashtag', text: '#日本', tag: '#日本' });
  });

  it('never drops characters', () => {
    for (const text of ['', 'plain', '#a #bb @abc. https://x.y/z?q=1)', 'emoji 🎉 #tag!']) {
      expect(join(text)).toBe(text);
    }
  });
});
