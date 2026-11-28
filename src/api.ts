// Client for the Just-Chords API (src/worker). In the Android app the pages
// are served from appassets.androidplatform.net, so the API is called on the
// real domain; song pages are fetched by the phone itself when possible.
import { onePerSong, parseSong, relevant, searchUrl, sourceOf, parseUgSearch, type FetchedSong, type LinkInfo, type SearchResult } from './lib/sources';
import type { PlaylistInfo, Profile, Rec } from './lib/recs';
import type { Level } from './lib/difficulty';
import type { Library } from './lib/sync';

interface NativeBridge {
  keepScreenOn?(on: boolean): void;
  fetchText?(id: string, url: string): void;
  googleSignIn?(id: string): void;
}
export const native = (): NativeBridge | undefined => (window as unknown as { JustChordsAndroid?: NativeBridge }).JustChordsAndroid;
export const inAndroidApp = () => location.hostname === 'appassets.androidplatform.net';
const BASE = typeof location !== 'undefined' && inAndroidApp() ? 'https://chords.justapps.it' : '';

// Native calls answer through window.__jcNative(id, ok, status, url, body).
const waiting = new Map<string, (r: { ok: boolean; status: number; url: string; body: string }) => void>();
(window as unknown as { __jcNative: unknown }).__jcNative = (id: string, ok: boolean, status: number, url: string, body: string) => {
  waiting.get(id)?.({ ok, status, url, body });
  waiting.delete(id);
};
export function callNative(fn: 'fetchText' | 'googleSignIn', arg?: string, timeout = 20000) {
  const id = Math.random().toString(36).slice(2);
  return new Promise<{ ok: boolean; status: number; url: string; body: string }>((resolve, reject) => {
    const b = native();
    if (!b?.[fn]) return reject(new Error('not available'));
    const t = setTimeout(() => {
      waiting.delete(id);
      reject(new Error('timeout'));
    }, timeout);
    waiting.set(id, (r) => {
      clearTimeout(t);
      resolve(r);
    });
    if (fn === 'fetchText') b.fetchText!(id, arg!);
    else b.googleSignIn!(id);
  });
}

const TOKEN = 'justchords-token';
export const getToken = () => {
  try {
    return localStorage.getItem(TOKEN);
  } catch {
    return null;
  }
};
export const setToken = (t: string | null) => {
  try {
    if (t) localStorage.setItem(TOKEN, t);
    else localStorage.removeItem(TOKEN);
  } catch {
    /* blocked */
  }
};

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { ...(init.body ? { 'content-type': 'application/json' } : {}) };
  const token = getToken();
  if (token) headers.authorization = `Bearer ${token}`;
  const r = await fetch(BASE + path, { ...init, headers: { ...headers, ...(init.headers as Record<string, string>) } });
  const data = (await r.json().catch(() => ({}))) as T & { error?: string };
  if (!r.ok) throw new ApiError(r.status, data.error ?? `HTTP ${r.status}`);
  return data;
}

// ------------------------------------------------------------------ chords

/** From the phone first (Android app), then through the Worker. */
async function phoneFetch(url: string): Promise<string | null> {
  if (!native()?.fetchText) return null;
  try {
    const r = await callNative('fetchText', url);
    return r.ok ? r.body : null;
  } catch {
    return null;
  }
}

export async function searchChords(q: string): Promise<{ results: SearchResult[]; errors: Record<string, unknown> }> {
  const viaWorker = api<{ results: SearchResult[]; errors: Record<string, unknown> }>(`/api/search?q=${encodeURIComponent(q)}`).catch(() => ({ results: [] as SearchResult[], errors: { all: 'offline' } }));
  const html = await phoneFetch(searchUrl('ug', q));
  const phoneUg = html ? relevant(parseUgSearch(html), q) : [];
  const w = await viaWorker;
  if (!phoneUg.length) return w;
  return { results: onePerSong([...phoneUg, ...w.results.filter((r) => r.source !== 'ug')]), errors: {} };
}

export async function fetchSong(url: string): Promise<FetchedSong> {
  if (!sourceOf(url)) throw new ApiError(400, 'unsupported site');
  const html = await phoneFetch(url);
  const local = html ? parseSong(html, url) : null;
  if (local) return local;
  return (await api<{ song: FetchedSong }>(`/api/song?url=${encodeURIComponent(url)}`)).song;
}

export const resolveLink = (url: string) => api<LinkInfo>(`/api/link?url=${encodeURIComponent(url)}`);
/** A ≤ 35 s WAV clip → words with times (Whisper on the server; the audio is not kept). */
export async function transcribe(wav: Uint8Array, offset: number): Promise<{ w: string; s: number; e: number }[]> {
  const r = await fetch(`${BASE}/api/transcribe?offset=${offset.toFixed(2)}`, { method: 'POST', headers: { 'content-type': 'audio/wav' }, body: new Blob([wav as BlobPart], { type: 'audio/wav' }) });
  const data = (await r.json().catch(() => ({}))) as { words?: { w: string; s: number; e: number }[]; error?: string };
  if (!r.ok) throw new ApiError(r.status, data.error ?? `HTTP ${r.status}`);
  return data.words ?? [];
}
export const findVideos = (q: string) => api<{ items: { id: string; title: string; channel: string }[] }>(`/api/yt?q=${encodeURIComponent(q)}`);

// ------------------------------------------------------------------ account

export interface User {
  sub: string;
  email: string;
  name: string;
  picture?: string;
}

/** Version of terms and privacy policy the user accepts at sign-in. */
export const TERMS_VERSION = '2026-11-28';
export const loginWithGoogle = (credential: string) =>
  api<Login>('/api/auth/google', { method: 'POST', body: JSON.stringify({ credential, consent: { age14: true, terms: TERMS_VERSION } }) });
export type Login = { token: string; user: User; profile: Profile | null; created?: boolean };
export const registerEmail = (email: string, password: string, name: string) =>
  api<Login>('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password, name, consent: { age14: true, terms: TERMS_VERSION } }) });
export const loginEmail = (email: string, password: string) => api<Login>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
export const fetchPlaylist = (url: string) => api<PlaylistInfo>(`/api/playlist?url=${encodeURIComponent(url)}`);
export const deleteAccountApi = () => api<{ deleted: boolean }>('/api/account', { method: 'DELETE' });
export const me = () => api<{ user: User; profile: Profile | null }>('/api/me');
export const putProfile = (p: Profile) => api<{ profile: Profile }>('/api/profile', { method: 'PUT', body: JSON.stringify(p) });
export const syncLibrary = (lib: Library) => api<Library>('/api/sync', { method: 'POST', body: JSON.stringify(lib) });
export const recommendations = (profile: Profile, level: Level, known: string[]) =>
  api<{ recs: Rec[]; genres: { id: string; it: string; en: string }[] }>('/api/recs', { method: 'POST', body: JSON.stringify({ profile, level, known }) });
