#!/usr/bin/env node
// Seeds a running Pulse with fictional demo users, posts, follows and engagement, through the
// public API only. Safe to re-run: existing users are signed in instead of re-created, though
// posts are added again.
//
//   node scripts/seed.mjs                         # http://localhost:3000/api
//   node scripts/seed.mjs http://localhost:8081/api
//
// Every demo account's password is "password123".

const BASE = (process.argv[2] ?? 'http://localhost:3000/api').replace(/\/$/, '');
const PASSWORD = 'password123';

const USERS = [
  ['nadia', 'Nadia Putri', 'Backend engineer. JVM, Postgres, and too much coffee.'],
  ['raka', 'Raka Wibowo', 'Distributed systems nerd. Kafka at work, Redis at home.'],
  ['sinta', 'Sinta Maharani', 'Frontend + design systems. React, CSS, accessibility.'],
  ['dimas', 'Dimas Pratama', 'ML engineer. Ranking, recsys, and honest offline metrics.'],
  ['ayu', 'Ayu Lestari', 'Product designer. I draw boxes and argue about spacing.'],
  ['bayu', 'Bayu Saputra', 'SRE. If it is not monitored it is not in production.'],
  ['citra', 'Citra Anggraini', 'Mobile dev. Kotlin by day, Jakarta street food by night.'],
  ['eko', 'Eko Nugroho', 'Startup CTO. Shipping beats polishing, until it does not.'],
];

const POSTS = {
  nadia: [
    'Virtual threads in Java 21 made our blocking JDBC code scale without a reactive rewrite. #java',
    'Keyset pagination > OFFSET. Your page 500 will thank you. #postgres',
    'Transactional outbox pattern: write the event in the same transaction as the change. No dual writes.',
  ],
  raka: [
    'Fan-out on write for timelines, fan-out on read for celebrities. The hybrid is the whole trick.',
    'Redis sorted sets are an underrated timeline primitive. Score by id, trim by rank. #redis',
    'At-least-once delivery means every consumer must be idempotent. Write that on the wall.',
  ],
  sinta: [
    'Dark mode is not inverting colors. It is a second palette with its own contrast checks. #css',
    'Shipped a feed UI today with zero icon fonts. Inline SVG all the way. #react',
    'Reminder: buttons need focus styles. Keyboard users exist.',
  ],
  dimas: [
    'Start with a transparent weighted ranker. You cannot debug a model you cannot explain.',
    'Log impressions with the model version and slot position, or your training data is useless later.',
    'Engagement velocity with a gravity term beats raw like counts for freshness. #recsys',
  ],
  ayu: [
    'Spacing scale: 4, 8, 12, 16, 24, 32. Pick one and stop debating.',
    'A good empty state tells you what to do next, not just that nothing is here.',
  ],
  bayu: [
    'Ran our stack with Redis killed. The feed fell back to Postgres and nobody noticed. That is the goal. #redis',
    'Your health check should test dependencies you actually need, not just return 200.',
  ],
  citra: [
    'Nasi goreng at 1am after a release is a tradition now.',
    'Offline-first mobile apps teach you the same lessons as distributed systems.',
  ],
  eko: [
    'Modular monolith first. Split into services when a team boundary demands it, not before.',
    'We moved to a 2-vCPU box and one JVM. Bills down 70%, latency unchanged.',
  ],
};

// Who follows whom: a small network with a couple of hubs, so every recommendation source fires.
const FOLLOWS = {
  nadia: ['raka', 'dimas', 'bayu'],
  raka: ['nadia', 'bayu', 'eko'],
  sinta: ['ayu', 'citra'],
  dimas: ['nadia', 'raka'],
  ayu: ['sinta'],
  bayu: ['raka', 'nadia', 'eko'],
  citra: ['sinta', 'eko'],
  eko: ['raka', 'nadia', 'dimas', 'bayu'],
};

async function call(method, path, token, body) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok && res.status !== 409) throw new Error(`${method} ${path} -> ${res.status} ${await res.text()}`);
  return res.status === 204 || res.status === 202 ? null : res.json().catch(() => null);
}

async function account([username, displayName, bio]) {
  let auth = await call('POST', '/auth/register', null, {
    username,
    email: `${username}@demo.pulse`,
    displayName,
    password: PASSWORD,
  });
  if (!auth?.accessToken) auth = await call('POST', '/auth/login', null, { login: username, password: PASSWORD });
  await call('PATCH', '/me', auth.accessToken, { bio });
  return auth.accessToken;
}

const pick = (list, n) => [...list].sort(() => Math.random() - 0.5).slice(0, n);

async function main() {
  console.log(`Seeding ${BASE}`);
  const tokens = {};
  for (const user of USERS) tokens[user[0]] = await account(user);
  console.log(`  ${USERS.length} users`);

  for (const [user, targets] of Object.entries(FOLLOWS)) {
    for (const target of targets) await call('PUT', `/users/${target}/follow`, tokens[user]);
  }

  const posts = [];
  for (const [user, texts] of Object.entries(POSTS)) {
    for (const content of texts) {
      const post = await call('POST', '/posts', tokens[user], { content });
      posts.push({ id: post.id, author: user });
    }
  }
  console.log(`  ${posts.length} posts`);

  let likes = 0;
  let reposts = 0;
  for (const user of Object.keys(tokens)) {
    for (const post of pick(posts.filter((p) => p.author !== user), 5)) {
      await call('PUT', `/posts/${post.id}/like`, tokens[user]);
      likes++;
    }
    for (const post of pick(posts.filter((p) => p.author !== user), 1)) {
      await call('PUT', `/posts/${post.id}/repost`, tokens[user]);
      reposts++;
    }
  }

  const replies = [
    ['raka', 0, 'Same here. Loom finally paid off.'],
    ['dimas', 3, 'And log the candidate source too, it makes offline analysis much easier.'],
    ['sinta', 7, 'Bookmarking this for our next design review.'],
  ];
  for (const [user, index, content] of replies) {
    await call('POST', '/posts', tokens[user], { content, replyToId: posts[index].id });
  }
  console.log(`  ${likes} likes, ${reposts} reposts, ${replies.length} replies`);
  console.log(`Done. Sign in as any of: ${USERS.map((u) => u[0]).join(', ')} (password: ${PASSWORD})`);
}

main().catch((err) => {
  console.error(err.message);
  process.exit(1);
});
