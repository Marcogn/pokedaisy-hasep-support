// Posts each website feedback submission (Firestore `feedback/{id}`, written by src/pages/feedback.astro
// under firestore.rules) on lidor30/pokedaisy, in the matching form's layout and labels: a game request or an
// idea as a discussion in its category (Game requests / Ideas), a bug as an issue. The submission is checked
// again here (the rules can't read every limit), one with lots of links is held back as likely spam, and the
// result is written onto the document: `issue` / `discussion` (number + url) or `status` ('held' / 'invalid'
// / 'failed'). The sender's contact never leaves Firestore.
//
// Needs the Blaze plan and a fine-grained GitHub token on that one repository with Issues: read and write
// and Discussions: read and write:
//   firebase functions:secrets:set GITHUB_TOKEN
//   firebase deploy --only functions,firestore:rules
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { onRequest } from 'firebase-functions/v2/https';
import { defineSecret } from 'firebase-functions/params';
import { logger } from 'firebase-functions';
import { initializeApp } from 'firebase-admin/app';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { clean, validate, linkCount, toPost } from './feedback-form.js';
import { SHARDS, filesOrigin, parseDownload, filePath, fileKey, sumShards } from './downloads.js';

initializeApp();

const GITHUB_TOKEN = defineSecret('GITHUB_TOKEN');
const OWNER = 'lidor30';
const NAME = 'pokedaisy';
/** More links than this in one submission: held for a look in the console instead of posted. */
const MAX_LINKS = 3;

export const feedbackToGithub = onDocumentCreated(
  { document: 'feedback/{id}', secrets: [GITHUB_TOKEN], region: 'us-central1', maxInstances: 2 },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data();
    const sub = clean(data.kind, data.values ?? {});
    const done = (fields) => snap.ref.update({ ...fields, processedAt: FieldValue.serverTimestamp() });
    if (!sub || Object.keys(validate({ ...sub, contact: '' })).length) return done({ status: 'invalid' });
    if (linkCount(sub) > MAX_LINKS) return done({ status: 'held' });
    const post = toPost(sub);
    try {
      if (post.category) {
        const created = await createDiscussion(post);
        return done({ discussion: { number: created.number, url: created.url } });
      }
      const { title, body, labels } = post;
      const created = await github(`https://api.github.com/repos/${OWNER}/${NAME}/issues`, { title, body, labels });
      return done({ issue: { number: created.number, url: created.html_url } });
    } catch (e) {
      logger.error('feedback: not posted', { id: event.params.id, error: String(e) });
      return done({ status: 'failed' });
    }
  },
);

async function github(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${GITHUB_TOKEN.value()}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'pokedaisy-feedback',
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`GitHub ${res.status}: ${(await res.text()).slice(0, 300)}`);
  return res.json();
}

async function graphql(query, variables) {
  const answer = await github('https://api.github.com/graphql', { query, variables });
  if (answer.errors?.length) throw new Error(`GitHub GraphQL: ${JSON.stringify(answer.errors).slice(0, 300)}`);
  return answer.data;
}

/** A discussion in `post.category` (by slug), labelled like the form. A label that doesn't take isn't a failure. */
async function createDiscussion(post) {
  const { repository: repo } = await graphql(
    `query($owner: String!, $name: String!, $slug: String!, $label: String!) {
      repository(owner: $owner, name: $name) { id discussionCategory(slug: $slug) { id } label(name: $label) { id } }
    }`,
    { owner: OWNER, name: NAME, slug: post.category, label: post.labels[0] ?? '' },
  );
  if (!repo?.discussionCategory) throw new Error(`no discussion category '${post.category}'`);
  const { createDiscussion: { discussion } } = await graphql(
    `mutation($repo: ID!, $category: ID!, $title: String!, $body: String!) {
      createDiscussion(input: { repositoryId: $repo, categoryId: $category, title: $title, body: $body }) {
        discussion { id number url }
      }
    }`,
    { repo: repo.id, category: repo.discussionCategory.id, title: post.title, body: post.body },
  );
  if (repo.label) {
    await graphql(
      `mutation($id: ID!, $labels: [ID!]!) { addLabelsToLabelable(input: { labelableId: $id, labelIds: $labels }) { clientMutationId } }`,
      { id: discussion.id, labels: [repo.label.id] },
    ).catch((e) => logger.warn('feedback: discussion not labelled', { number: discussion.number, error: String(e) }));
  }
  return discussion;
}

// The download counter (downloads.js): /dl/<tag>/<file> counts and redirects to the file, /api/downloads/v1/count.json
// sums the counts. Both are Hosting rewrites to this one function (firebase.json).
/** Where downloads go now: FILES_ORIGIN, or the r2.dev fallback while it lasts (downloads.js). */
const files = () => process.env.FILES_ORIGIN || filesOrigin();
/** Whether a release file is in the bucket, by a HEAD of it, remembered a while per instance. */
const seen = new Map();
async function inBucket(path) {
  const hit = seen.get(path);
  if (hit && Date.now() - hit.at < 10 * 60_000) return hit.ok;
  let ok = false;
  try { ok = (await fetch(files() + path, { method: 'HEAD', redirect: 'manual' })).status === 200; } catch {}
  seen.set(path, { ok, at: Date.now() });
  return ok;
}

export const downloads = onRequest({ region: 'us-central1', maxInstances: 10, concurrency: 80 }, async (req, res) => {
  if (req.path === '/api/downloads/v1/count.json') {
    try {
      const snap = await getFirestore().collection('downloadCounts').get();
      const sum = sumShards(snap.docs.map((d) => d.data()));
      res.set('Cache-Control', 'public, max-age=60, s-maxage=300');
      res.json({ site: sum.total, tags: sum.tags, files: sum.files });
    } catch (e) {
      logger.error('download count', e);
      res.set('Cache-Control', 'no-store').status(503).json({ error: 'unavailable' });
    }
    return;
  }
  const dl = parseDownload(req.path);
  res.set('Cache-Control', 'no-store');
  if (!dl) { res.status(404).send('Not found'); return; }
  const path = filePath(dl);
  // The download never waits on a failed count: the file is served whatever happens here.
  // Only a real download counts (a HEAD, a link checker's look, doesn't).
  if (req.method === 'GET' && dl.counts && await inBucket(path)) {
    const shard = getFirestore().collection('downloadCounts').doc(String(Math.floor(Math.random() * SHARDS)));
    try {
      await shard.set({ total: FieldValue.increment(1), files: { [fileKey(dl)]: FieldValue.increment(1) } }, { merge: true });
    } catch (e) { logger.error('download count', e); }
  }
  res.redirect(302, files() + path);
});
