// Just Chords Worker: the static app (assets) plus a small API.
//   GET  /api/search?q=        chords on Ultimate Guitar and Accordi e Spartiti
//   GET  /api/song?url=        one song page → text to import
//   GET  /api/link?url=        YouTube / YouTube Music / Spotify link → title, artist, video
//   GET  /api/yt?q=            YouTube video for a song (needs YOUTUBE_API_KEY)
//   POST /api/recs             recommendations for a taste profile
//   POST /api/auth/google      Google ID token → session token
//   POST /api/auth/register    email + password → new account
//   POST /api/auth/login       email + password → session token
//   GET  /api/playlist?url=    Spotify / YouTube / Deezer playlist → artists and genres
//   GET  /api/me               account, profile
//   PUT  /api/profile          save profile
//   POST /api/sync             merge the library with the account copy
//   POST /api/transcribe       a WAV clip (≤ 35 s) → words with timestamps (Whisper, Workers AI)
import { DEEZER_GENRES, parseSpotifyPlaylist, playlistArtists, playlistRef, aesApiSearchUrl, isShortTiktok, onePerSong, socialVideo, relevant, parseAesApi, parseAesSearch, parseSong, parseSpotify, parseUgSearch, searchUrl, sourceOf, splitVideoTitle, youtubeId, type SearchResult } from '../lib/sources';
import { CURATED, GENRES, emptyProfile, plan, rank, type Candidate, type Profile } from '../lib/recs';
import { mergeLibrary, type Library } from '../lib/sync';
import type { Level } from '../lib/difficulty';

interface KV {
  get(key: string, type: 'json'): Promise<any>;
  put(key: string, value: string, opts?: { expirationTtl?: number }): Promise<void>;
  delete(key: string): Promise<void>;
}
interface Env {
  ASSETS: { fetch(req: Request): Promise<Response> };
  USERS: KV;
  GOOGLE_CLIENT_ID: string;
  SESSION_SECRET?: string;
  YOUTUBE_API_KEY?: string;
  AI?: { run(model: string, input: Record<string, unknown>): Promise<unknown> };
}

const UA = 'Mozilla/5.0 (compatible; JustChords/1.1; +https://chords.justapps.it)';
const ORIGINS = ['https://chords.justapps.it', 'https://appassets.androidplatform.net', 'http://localhost:5173', 'http://localhost:4173'];
const CACHE_V = '5';
const edge = (caches as unknown as { default: Cache }).default;

class HttpError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

export default {
  async fetch(req: Request, env: Env, ctx: { waitUntil(p: Promise<unknown>): void }): Promise<Response> {
    const url = new URL(req.url);
    if (!url.pathname.startsWith('/api/')) return env.ASSETS.fetch(req);
    const origin = req.headers.get('origin') ?? '';
    const cors: Record<string, string> = ORIGINS.includes(origin)
      ? { 'access-control-allow-origin': origin, 'access-control-allow-headers': 'authorization, content-type', 'access-control-allow-methods': 'GET, POST, PUT, DELETE, OPTIONS', 'access-control-max-age': '86400', vary: 'origin' }
      : {};
    if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
    const json = (data: unknown, status = 200, extra: Record<string, string> = {}) =>
      new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json; charset=utf-8', ...cors, ...extra } });

    try {
      // Public, cacheable GET endpoints.
      const cacheable: Record<string, (p: URLSearchParams) => Promise<{ data: unknown; ttl: number }>> = {
        '/api/search': (p) => search(p.get('q') ?? ''),
        '/api/song': (p) => song(p.get('url') ?? ''),
        '/api/link': (p) => link(p.get('url') ?? ''),
        '/api/yt': (p) => yt(p.get('q') ?? '', env),
        '/api/playlist': (p) => playlist(p.get('url') ?? '', env),
      };
      const handler = cacheable[url.pathname];
      if (handler && req.method === 'GET') {
        // Bump CACHE_V when the parsers change, so stale answers are not served.
        const key = new Request(url.toString() + (url.search ? '&' : '?') + 'v=' + CACHE_V);
        const hit = await edge.match(key);
        if (hit) return json(await hit.json());
        const { data, ttl } = await handler(url.searchParams);
        ctx.waitUntil(edge.put(key, new Response(JSON.stringify(data), { headers: { 'content-type': 'application/json', 'cache-control': `public, max-age=${ttl}` } })));
        return json(data);
      }
      if (url.pathname === '/api/transcribe' && req.method === 'POST') return json(await transcribe(req, url, env));
      if (url.pathname === '/api/recs' && req.method === 'POST') return json(await recommend(await req.json()));
      if (url.pathname === '/api/auth/google' && req.method === 'POST') {
        const { credential, consent } = (await req.json()) as { credential?: string; consent?: Consent };
        checkConsent(consent);
        const user = await verifyGoogle(String(credential ?? ''), env.GOOGLE_CLIENT_ID);
        return json(await startSession(user, consent!, env));
      }
      if (url.pathname === '/api/auth/register' && req.method === 'POST') {
        await limit(req, env, 'reg', 10);
        const b = (await req.json()) as { email?: string; password?: string; name?: string; consent?: Consent };
        checkConsent(b.consent);
        const email = normEmail(b.email);
        const password = String(b.password ?? '');
        if (password.length < 8 || password.length > 200) throw new HttpError(400, 'weak password');
        if (await env.USERS.get(`e:${email}`, 'json')) throw new HttpError(409, 'email taken');
        const sub = `em_${crypto.randomUUID()}`;
        const salt = b64u.enc(crypto.getRandomValues(new Uint8Array(16)));
        await env.USERS.put(`e:${email}`, JSON.stringify({ sub, salt, hash: await pbkdf2(password, salt), iter: PBKDF2_ITER, created: Date.now() }));
        const name = String(b.name ?? '').trim().slice(0, 60) || email.split('@')[0];
        return json(await startSession({ sub, email, name }, b.consent!, env));
      }
      if (url.pathname === '/api/auth/login' && req.method === 'POST') {
        await limit(req, env, 'login', 20);
        const b = (await req.json()) as { email?: string; password?: string };
        const email = normEmail(b.email);
        const rec = (await env.USERS.get(`e:${email}`, 'json')) as { sub: string; salt: string; hash: string; iter?: number } | null;
        // Same work and the same answer whether the email exists or not.
        const hash = await pbkdf2(String(b.password ?? ''), rec?.salt ?? 'x', rec?.iter);
        if (!rec || !timingSafeEqual(hash, rec.hash)) throw new HttpError(401, 'wrong email or password');
        const u = (await env.USERS.get(`u:${rec.sub}`, 'json')) as (User & { consent?: Consent }) | null;
        if (!u) throw new HttpError(401, 'wrong email or password');
        return json(await startSession({ sub: rec.sub, email, name: u.name }, u.consent as Consent, env));
      }
      const me = await session(req, env);
      if (url.pathname === '/api/account' && req.method === 'DELETE') {
        // Right to erasure: everything stored for this account, at once.
        await Promise.all([
          env.USERS.delete(`u:${me.sub}`),
          env.USERS.delete(`p:${me.sub}`),
          env.USERS.delete(`l:${me.sub}`),
          me.sub.startsWith('em_') ? env.USERS.delete(`e:${me.email}`) : Promise.resolve(),
        ]);
        return json({ deleted: true });
      }
      if (url.pathname === '/api/me' && req.method === 'GET') return json({ user: me, profile: await env.USERS.get(`p:${me.sub}`, 'json') });
      if (url.pathname === '/api/profile' && req.method === 'PUT') {
        const incoming = (await req.json()) as Profile;
        const stored = (await env.USERS.get(`p:${me.sub}`, 'json')) as Profile | null;
        const next = !stored || (incoming.updated ?? 0) >= (stored.updated ?? 0) ? clampProfile(incoming) : stored;
        if (next !== stored) await env.USERS.put(`p:${me.sub}`, JSON.stringify(next));
        return json({ profile: next });
      }
      if (url.pathname === '/api/sync' && req.method === 'POST') {
        const body = await req.text();
        if (body.length > 20_000_000) throw new HttpError(413, 'library too large');
        const stored = ((await env.USERS.get(`l:${me.sub}`, 'json')) as Library | null) ?? { songs: [], setlists: [], deleted: {} };
        const merged = mergeLibrary(stored, JSON.parse(body) as Library);
        await env.USERS.put(`l:${me.sub}`, JSON.stringify(merged));
        return json(merged);
      }
      throw new HttpError(404, 'not found');
    } catch (e) {
      const status = e instanceof HttpError ? e.status : 500;
      return json({ error: (e as Error).message ?? String(e) }, status);
    }
  },
};

// ------------------------------------------------------------------ sources

async function get(url: string, accept = 'text/html'): Promise<Response> {
  return fetch(url, { headers: { 'user-agent': UA, accept, 'accept-language': 'it,en;q=0.8' }, redirect: 'follow' });
}

async function search(q: string) {
  q = q.trim().slice(0, 120);
  if (q.length < 2) throw new HttpError(400, 'query too short');
  const errors: Record<string, number | string> = {};
  const ug = get(searchUrl('ug', q))
    .then(async (r) => (r.ok ? relevant(parseUgSearch(await r.text()), q) : ((errors.ug = r.status), [])))
    .catch((e) => ((errors.ug = String(e)), [] as SearchResult[]));
  const aes = get(aesApiSearchUrl(q), 'application/json')
    .then(async (r) => (r.ok ? relevant(parseAesApi(await r.json()), q) : []))
    .catch(() => [] as SearchResult[])
    .then(async (list) => {
      if (list.length) return list;
      const r = await get(searchUrl('aes', q));
      if (!r.ok) errors.aes = r.status;
      return r.ok ? relevant(parseAesSearch(await r.text()), q) : [];
    })
    .catch((e) => ((errors.aes = String(e)), [] as SearchResult[]));
  const [a, b] = await Promise.all([ug, aes]);
  return { data: { results: onePerSong([...a, ...b]), errors }, ttl: a.length + b.length ? 6 * 3600 : 300 };
}

async function song(url: string) {
  if (!sourceOf(url)) throw new HttpError(400, 'unsupported site');
  const r = await get(url);
  if (!r.ok) throw new HttpError(502, `source answered ${r.status}`);
  const s = parseSong(await r.text(), r.url || url);
  if (!s) throw new HttpError(422, 'no chords found on the page');
  return { data: { song: s }, ttl: 86400 };
}

async function link(url: string) {
  const id = youtubeId(url);
  if (id) {
    const r = await get(`https://www.youtube.com/oembed?format=json&url=${encodeURIComponent(`https://www.youtube.com/watch?v=${id}`)}`, 'application/json');
    if (!r.ok) return { data: { kind: 'youtube', youtubeId: id, url }, ttl: 3600 };
    const o = (await r.json()) as { title?: string; author_name?: string };
    return { data: { kind: 'youtube', youtubeId: id, url, ...splitVideoTitle(o.title ?? '', o.author_name ?? '') }, ttl: 7 * 86400 };
  }
  let u: URL;
  try {
    u = new URL(url);
  } catch {
    throw new HttpError(400, 'not a link');
  }
  // TikTok / Instagram: the video id for the embedded player, and the caption when there is one.
  if (isShortTiktok(url)) {
    const r = await fetch(u.toString(), { headers: { 'user-agent': UA }, redirect: 'follow' });
    u = new URL(r.url || u.toString());
  }
  const social = socialVideo(u.toString());
  if (social?.platform === 'tiktok') {
    const canonical = `https://www.tiktok.com/@_/video/${social.id}`;
    const o = await get(`https://www.tiktok.com/oembed?url=${encodeURIComponent(u.toString().includes('/video/') ? u.toString().split('?')[0] : canonical)}`, 'application/json').catch(() => null);
    const d = o?.ok ? ((await o.json()) as { title?: string; author_name?: string }) : {};
    return { data: { kind: 'tiktok', url, videoId: social.id, title: (d.title ?? '').slice(0, 140), artist: d.author_name ?? '' }, ttl: 7 * 86400 };
  }
  if (social?.platform === 'instagram') return { data: { kind: 'instagram', url, videoId: social.id, title: '', artist: '' }, ttl: 7 * 86400 };
  if (u.hostname === 'open.spotify.com' || u.hostname === 'spotify.link') {
    const r = await get(u.toString());
    const info = r.ok ? parseSpotify(await r.text()) : null;
    if (info) return { data: { kind: 'spotify', url, ...info }, ttl: 7 * 86400 };
    const o = await get(`https://open.spotify.com/oembed?url=${encodeURIComponent(u.toString())}`, 'application/json');
    if (o.ok) {
      const d = (await o.json()) as { title?: string };
      return { data: { kind: 'spotify', url, title: d.title ?? '', artist: '' }, ttl: 86400 };
    }
    throw new HttpError(502, 'spotify unavailable');
  }
  throw new HttpError(400, 'unsupported link');
}

// ------------------------------------------------------------------ lyrics

const WHISPER = '@cf/openai/whisper-large-v3-turbo';
const DAILY_SECONDS = 20 * 60;

interface Word {
  w: string;
  s: number;
  e: number;
}

/** Duration of a 16-bit PCM WAV, or null if it is not one. */
function wavSeconds(b: Uint8Array): number | null {
  const v = new DataView(b.buffer, b.byteOffset, b.byteLength);
  if (b.length < 44 || v.getUint32(0, false) !== 0x52494646 || v.getUint32(8, false) !== 0x57415645) return null;
  const channels = v.getUint16(22, true);
  const rate = v.getUint32(24, true);
  const bits = v.getUint16(34, true);
  if (!rate || !channels || bits !== 16) return null;
  return (b.length - 44) / (rate * channels * 2);
}

function toBase64(b: Uint8Array): string {
  let s = '';
  for (let i = 0; i < b.length; i += 0x8000) s += String.fromCharCode(...b.subarray(i, i + 0x8000));
  return btoa(s);
}

/** Whisper output (segments with words, or a flat word list) → words with times. */
export function whisperWords(out: unknown, offset = 0): { words: Word[]; text: string } {
  const o = (out ?? {}) as { text?: string; words?: { word: string; start: number; end: number }[]; segments?: { start: number; end: number; text: string; words?: { word: string; start: number; end: number }[] }[] };
  const raw = o.segments?.some((s) => s.words?.length) ? o.segments.flatMap((s) => s.words ?? []) : (o.words ?? []);
  let words = raw.filter((w) => w && typeof w.word === 'string' && w.word.trim()).map((w) => ({ w: w.word.trim(), s: +(w.start + offset).toFixed(2), e: +(w.end + offset).toFixed(2) }));
  // No word timings: spread each segment's words over its span.
  if (!words.length && o.segments?.length) {
    words = o.segments.flatMap((seg) => {
      const ws = seg.text.trim().split(/\s+/).filter(Boolean);
      const step = (seg.end - seg.start) / Math.max(1, ws.length);
      return ws.map((w, i) => ({ w, s: +(seg.start + offset + i * step).toFixed(2), e: +(seg.start + offset + (i + 1) * step).toFixed(2) }));
    });
  }
  return { words, text: (o.text ?? words.map((w) => w.w).join(' ')).trim() };
}

async function transcribe(req: Request, url: URL, env: Env) {
  if (!env.AI) throw new HttpError(501, 'no-ai');
  const audio = new Uint8Array(await req.arrayBuffer());
  if (audio.length > 3_000_000) throw new HttpError(413, 'clip too long');
  const secs = wavSeconds(audio);
  if (secs === null) throw new HttpError(400, 'send a 16-bit WAV');
  if (secs > 35) throw new HttpError(413, 'clip too long');
  // Rough daily limit per IP, so the endpoint is not a free transcription service.
  const ip = req.headers.get('cf-connecting-ip') ?? 'unknown';
  const key = `tr:${ip}:${new Date().toISOString().slice(0, 10)}`;
  const used = Number((await env.USERS.get(key, 'json')) ?? 0);
  if (used + secs > DAILY_SECONDS) throw new HttpError(429, 'daily limit');
  await env.USERS.put(key, JSON.stringify(Math.ceil(used + secs)));
  const lang = (url.searchParams.get('lang') ?? '').match(/^[a-z]{2}$/)?.[0];
  const offset = Math.max(0, Math.min(3600, Number(url.searchParams.get('offset')) || 0));
  // The audio is only passed to the model, never stored.
  const out = await env.AI.run(WHISPER, { audio: toBase64(audio), task: 'transcribe', ...(lang ? { language: lang } : {}) });
  return whisperWords(out, offset);
}

async function yt(q: string, env: Env) {
  if (!env.YOUTUBE_API_KEY) throw new HttpError(501, 'no-key');
  q = q.trim().slice(0, 120);
  const r = await fetch(
    `https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=5&q=${encodeURIComponent(q)}&key=${env.YOUTUBE_API_KEY}`,
    { headers: { referer: 'https://chords.justapps.it/' } },
  );
  if (!r.ok) throw new HttpError(502, `youtube ${r.status}`);
  const d = (await r.json()) as { items?: { id: { videoId: string }; snippet: { title: string; channelTitle: string } }[] };
  const items = (d.items ?? []).map((i) => ({ id: i.id.videoId, title: i.snippet.title, channel: i.snippet.channelTitle }));
  return { data: { items }, ttl: 7 * 86400 };
}

// ------------------------------------------------------------------ recommendations

interface DzTrack {
  title_short?: string;
  title: string;
  artist: { name: string; id: number };
  album?: { cover_medium?: string };
  rank?: number;
  preview?: string;
}

// Workers allow 50 subrequests per call (free plan): keep a budget, and let
// the edge cache Deezer answers (cf.cacheTtl) so repeated calls are cheap.
function deezerClient(budget: number) {
  const deezer = async <T,>(path: string): Promise<T | null> => {
    if (budget-- <= 0) return null;
    const r = await fetch(`https://api.deezer.com${path}`, { headers: { 'user-agent': UA }, cf: { cacheTtl: 86400, cacheEverything: true } } as RequestInit).catch(() => null);
    if (!r?.ok) return null;
    const data = (await r.json()) as T & { error?: unknown };
    return data.error ? null : data;
  };
  return {
    deezer,
    // Several artists share a name: take the most followed exact match.
    artistId: async (name: string) => {
      const list = (await deezer<{ data: { id: number; name: string; nb_fan?: number }[] }>(`/search/artist?limit=8&q=${encodeURIComponent(name)}`))?.data ?? [];
      const exact = list.filter((a) => a.name.toLowerCase() === name.toLowerCase());
      const best = (exact.length ? exact : list).sort((x, y) => (y.nb_fan ?? 0) - (x.nb_fan ?? 0))[0];
      return best?.id;
    },
    topTracks: async (id: number, n: number) => (await deezer<{ data: DzTrack[] }>(`/artist/${id}/top?limit=${n}`))?.data ?? [],
  };
}
const toCand = (t: DzTrack, reason: Candidate['reason'], genres?: string[]): Candidate => ({
  title: (t.title_short ?? t.title)
    .replace(/\s*[([][^)\]]*(remaster|live|version|mono|stereo|edit)[^)\]]*[)\]]/gi, '')
    .replace(/\s*-\s*(\d{4}\s*)?(remaster(ed)?|live|mono|stereo).*$/i, '')
    .trim(),
  artist: t.artist.name,
  cover: t.album?.cover_medium,
  preview: t.preview,
  rank: t.rank,
  genres,
  reason,
});

async function recommend(body: { profile?: Profile; level?: Level; known?: string[] }) {
  const p = clampProfile(body.profile ?? emptyProfile());
  const level = ([1, 2, 3] as const).includes(body.level as Level) ? (body.level as Level) : 1;
  const seed = Math.floor(Date.now() / 864e5);
  const { deezer, artistId, topTracks } = deezerClient(44);
  const plan0 = plan(p, seed);
  // Fewer artists when there are many genres, so covers still fit the budget.
  const top = plan0.top.slice(0, 3);
  const seeds = plan0.seeds.slice(0, 6);
  const cands: Candidate[] = [];
  const jobs: Promise<void>[] = [];
  for (const a of top)
    jobs.push(
      (async () => {
        const id = await artistId(a);
        if (!id) return;
        const own = await topTracks(id, 4);
        own.forEach((t) => cands.push(toCand(t, { type: 'more', name: t.artist.name })));
        // Deezer's "related" mixes in tiny artists: keep well-known ones.
        const rel = ((await deezer<{ data: { id: number; name: string; nb_fan?: number }[] }>(`/artist/${id}/related?limit=20`))?.data ?? [])
          .filter((r) => (r.nb_fan ?? 0) >= 250_000)
          .sort((x, y) => (y.nb_fan ?? 0) - (x.nb_fan ?? 0));
        const display = own[0]?.artist.name ?? a;
        await Promise.all(rel.slice(0, 2).map(async (r) => (await topTracks(r.id, 2)).forEach((t) => cands.push(toCand(t, { type: 'artist', name: display })))));
      })(),
    );
  for (const s of seeds)
    jobs.push(
      (async () => {
        const id = await artistId(s.artist);
        if (id) (await topTracks(id, 4)).forEach((t) => cands.push(toCand(t, { type: 'genre', name: s.genre }, [s.genre])));
      })(),
    );
  await Promise.all(jobs);
  for (const c of CURATED)
    if (p.genres.length ? c.genres.some((g) => p.genres.includes(g)) : Math.abs(c.level - level) <= 1)
      cands.push({ title: c.title, artist: c.artist, level: c.level, genres: c.genres, reason: { type: 'level' } });
  const recs = rank(cands, p, { level, known: new Set((body.known ?? []).slice(0, 5000)), seed });
  // Covers for the curated songs.
  await Promise.all(
    recs
      .filter((r) => !r.cover)
      .map(async (r) => {
        const d = await deezer<{ data: DzTrack[] }>(`/search?limit=1&q=${encodeURIComponent(`${r.artist} ${r.title}`)}`);
        r.cover = d?.data?.[0]?.album?.cover_medium;
        r.preview = d?.data?.[0]?.preview;
        r.rank = d?.data?.[0]?.rank;
      }),
  );
  return { recs, genres: GENRES.map(({ id, it, en }) => ({ id, it, en })) };
}

function clampProfile(p: Partial<Profile>): Profile {
  const e = emptyProfile();
  const nums = (o: unknown) =>
    Object.fromEntries(
      Object.entries(o && typeof o === 'object' ? o : {})
        .filter(([, v]) => typeof v === 'number' && Number.isFinite(v))
        .slice(0, 500),
    ) as Record<string, number>;
  const strs = (a: unknown, n: number) => (Array.isArray(a) ? a.filter((x) => typeof x === 'string').slice(-n) : []);
  return {
    genres: strs(p.genres, 20).filter((g) => GENRES.some((x) => x.id === g)),
    done: strs(p.done, 1000).filter((x) => x.length <= 80),
    onboarded: typeof p.onboarded === 'number' ? p.onboarded : undefined,
    level: ([0, 1, 2, 3] as const).includes(p.level as 0) ? (p.level as Profile['level']) : e.level,
    artists: nums(p.artists),
    genreBoost: nums(p.genreBoost),
    liked: strs(p.liked, 300),
    disliked: strs(p.disliked, 300),
    updated: typeof p.updated === 'number' ? p.updated : 0,
  };
}

// ------------------------------------------------------------------ auth

interface User {
  sub: string;
  email: string;
  name: string;
  picture?: string;
}

const b64u = {
  enc: (buf: ArrayBuffer | Uint8Array) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''),
  dec: (s: string) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4)), (c) => c.charCodeAt(0)),
};
const utf8 = new TextEncoder();

let jwks: { keys: (JsonWebKey & { kid: string })[]; at: number } | null = null;

async function verifyGoogle(jwt: string, clientId: string): Promise<User> {
  const [h, p, s] = jwt.split('.');
  if (!h || !p || !s) throw new HttpError(400, 'bad token');
  const header = JSON.parse(new TextDecoder().decode(b64u.dec(h))) as { kid: string; alg: string };
  if (header.alg !== 'RS256') throw new HttpError(400, 'bad alg');
  if (!jwks || Date.now() - jwks.at > 3600_000 || !jwks.keys.some((k) => k.kid === header.kid)) {
    const r = await fetch('https://www.googleapis.com/oauth2/v3/certs');
    jwks = { ...((await r.json()) as { keys: (JsonWebKey & { kid: string })[] }), at: Date.now() };
  }
  const jwk = jwks.keys.find((k) => k.kid === header.kid);
  if (!jwk) throw new HttpError(401, 'unknown key');
  const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
  const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, b64u.dec(s), utf8.encode(`${h}.${p}`));
  if (!ok) throw new HttpError(401, 'bad signature');
  const c = JSON.parse(new TextDecoder().decode(b64u.dec(p))) as Record<string, unknown>;
  if (c.aud !== clientId) throw new HttpError(401, 'wrong audience');
  if (c.iss !== 'accounts.google.com' && c.iss !== 'https://accounts.google.com') throw new HttpError(401, 'wrong issuer');
  if (typeof c.exp !== 'number' || c.exp * 1000 < Date.now()) throw new HttpError(401, 'expired');
  return { sub: String(c.sub), email: String(c.email ?? ''), name: String(c.name ?? c.email ?? ''), picture: typeof c.picture === 'string' ? c.picture : undefined };
}

async function hmac(env: Env) {
  if (!env.SESSION_SECRET) throw new HttpError(500, 'sessions not configured');
  return crypto.subtle.importKey('raw', utf8.encode(env.SESSION_SECRET), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign', 'verify']);
}

async function sign(data: User & { exp: number }, env: Env) {
  const body = b64u.enc(utf8.encode(JSON.stringify(data)));
  return `${body}.${b64u.enc(await crypto.subtle.sign('HMAC', await hmac(env), utf8.encode(body)))}`;
}

async function session(req: Request, env: Env): Promise<User> {
  const token = req.headers.get('authorization')?.replace(/^Bearer\s+/i, '') ?? '';
  const [body, mac] = token.split('.');
  if (!body || !mac) throw new HttpError(401, 'login required');
  const ok = await crypto.subtle.verify('HMAC', await hmac(env), b64u.dec(mac), utf8.encode(body));
  if (!ok) throw new HttpError(401, 'bad session');
  const d = JSON.parse(new TextDecoder().decode(b64u.dec(body))) as User & { exp: number };
  if (d.exp < Date.now()) throw new HttpError(401, 'session expired');
  // A deleted account signs out everywhere.
  if (!(await env.USERS.get(`u:${d.sub}`, 'json'))) throw new HttpError(401, 'account deleted');
  return { sub: d.sub, email: d.email, name: d.name, picture: d.picture };
}

type Consent = { age14?: boolean; terms?: string };

// Accounts are for people aged 14 or over who accepted terms and privacy policy.
function checkConsent(c: Consent | undefined) {
  if (c?.age14 !== true || typeof c.terms !== 'string') throw new HttpError(400, 'consent required');
}

/** Stores the account (first time: created) and signs a session. */
async function startSession(user: User, consent: Consent, env: Env) {
  const token = await sign({ ...user, exp: Date.now() + 180 * 864e5 }, env);
  const stored = (await env.USERS.get(`p:${user.sub}`, 'json')) as Profile | null;
  const prev = (await env.USERS.get(`u:${user.sub}`, 'json')) as { created?: number } | null;
  await env.USERS.put(
    `u:${user.sub}`,
    JSON.stringify({ ...user, created: prev?.created ?? Date.now(), last: Date.now(), consent: { age14: true, terms: String(consent.terms ?? '').slice(0, 20), at: Date.now() } }),
  );
  return { token, user, profile: stored, created: !prev };
}

const normEmail = (e: unknown) => {
  const s = String(e ?? '').trim().toLowerCase();
  if (s.length > 200 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)) throw new HttpError(400, 'bad email');
  return s;
};

// Workers allow PBKDF2 up to 100 000 iterations.
const PBKDF2_ITER = 100_000;
async function pbkdf2(password: string, salt: string, iter = PBKDF2_ITER) {
  const key = await crypto.subtle.importKey('raw', utf8.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: utf8.encode(salt), iterations: iter }, key, 256);
  return b64u.enc(bits);
}
function timingSafeEqual(a: string, b: string) {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

/** At most n attempts per IP per hour. */
async function limit(req: Request, env: Env, what: string, n: number) {
  const ip = req.headers.get('cf-connecting-ip') ?? 'unknown';
  const key = `rl:${what}:${ip}:${new Date().toISOString().slice(0, 13)}`;
  const count = ((await env.USERS.get(key, 'json')) as number | null) ?? 0;
  if (count >= n) throw new HttpError(429, 'too many attempts, try again later');
  await env.USERS.put(key, JSON.stringify(count + 1), { expirationTtl: 3700 });
}

// ------------------------------------------------------------------ playlists

async function playlist(url: string, env: Env) {
  const ref = playlistRef(url);
  if (!ref) throw new HttpError(400, 'unsupported playlist link');
  let name = '';
  let tracks: { title: string; artist: string }[] = [];
  if (ref.source === 'spotify') {
    const r = await get(`https://open.spotify.com/embed/playlist/${ref.id}`);
    const p = r.ok ? parseSpotifyPlaylist(await r.text()) : null;
    if (!p) throw new HttpError(502, `spotify ${r.status}`);
    ({ name, tracks } = p);
  } else if (ref.source === 'deezer') {
    const r = await get(`https://api.deezer.com/playlist/${ref.id}`, 'application/json');
    const d = (await r.json()) as { title?: string; tracks?: { data?: { title: string; artist: { name: string } }[] }; error?: unknown };
    if (!r.ok || d.error) throw new HttpError(502, 'deezer');
    name = d.title ?? '';
    tracks = (d.tracks?.data ?? []).map((t) => ({ title: t.title, artist: t.artist.name }));
  } else {
    if (!env.YOUTUBE_API_KEY) throw new HttpError(501, 'no-key');
    const headers = { referer: 'https://chords.justapps.it/' };
    const meta = (await (await fetch(`https://www.googleapis.com/youtube/v3/playlists?part=snippet&id=${ref.id}&key=${env.YOUTUBE_API_KEY}`, { headers })).json()) as { items?: { snippet: { title: string } }[] };
    name = meta.items?.[0]?.snippet.title ?? '';
    const r = await fetch(`https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId=${ref.id}&key=${env.YOUTUBE_API_KEY}`, { headers });
    if (!r.ok) throw new HttpError(r.status === 404 ? 404 : 502, `youtube ${r.status}`);
    const d = (await r.json()) as { items?: { snippet: { title: string; videoOwnerChannelTitle?: string } }[] };
    tracks = (d.items ?? [])
      .filter((i) => i.snippet.title !== 'Private video' && i.snippet.title !== 'Deleted video')
      .map((i) => splitVideoTitle(i.snippet.title, (i.snippet.videoOwnerChannelTitle ?? '').replace(/ - Topic$/, '').replace(/VEVO$/i, '')));
  }
  tracks = tracks.filter((t) => t.artist).slice(0, 200);
  const artists = playlistArtists(tracks);
  // Genres: our seed artists first, then Deezer's album genre of the top artists.
  const votes = new Map<string, number>();
  const vote = (g: string, w: number) => votes.set(g, (votes.get(g) ?? 0) + w);
  for (const a of artists) for (const g of GENRES) if (g.seeds.some((x) => x.toLowerCase() === a.name.toLowerCase())) vote(g.id, a.n * 2);
  const { deezer, artistId, topTracks } = deezerClient(30);
  await Promise.all(
    artists.slice(0, 8).map(async (a) => {
      const id = await artistId(a.name);
      if (!id) return;
      const album = (await topTracks(id, 1))[0] as (DzTrack & { album?: { id?: number } }) | undefined;
      if (!album?.album?.id) return;
      const al = await deezer<{ genres?: { data?: { name: string }[] } }>(`/album/${album.album.id}`);
      for (const g of al?.genres?.data ?? []) if (DEEZER_GENRES[g.name]) vote(DEEZER_GENRES[g.name], a.n);
    }),
  );
  const genres = [...votes.entries()].sort((x, y) => y[1] - x[1]).map(([g]) => g).slice(0, 4);
  return { data: { name, source: ref.source, tracks: tracks.slice(0, 100), artists: artists.slice(0, 40), genres }, ttl: 86400 };
}
