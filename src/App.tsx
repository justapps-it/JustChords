import { useEffect } from 'react';
import { Analyze } from './components/Analyze';
import { Dictionary } from './components/Dictionary';
import { Editor } from './components/Editor';
import { Find } from './components/Find';
import { Legal } from './components/Legal';
import { ProfilePage } from './components/ProfilePage';
import { CoursePage, LearnPage, LessonPage, SongLearn } from './components/Learn';
import { Onboarding, Welcome } from './components/Onboarding';
import { initAccount, useAccount, welcomed } from './account';
import { linkKind } from './lib/sources';
import { Home } from './components/Home';
import { Import } from './components/Import';
import { Listen } from './components/Listen';
import { MetronomeView } from './components/MetronomeView';
import { Setlist } from './components/Setlist';
import { SongView } from './components/SongView';
import { Tuner } from './components/Tuner';
import { useT } from './i18n';
import { go, useRoute } from './router';
import { setSettings, useSettings } from './settings';
import { load, useLibrary } from './store';

const REPO = 'https://github.com/justapps-it/Just-Chords';

export default function App() {
  const t = useT();
  const st = useSettings();
  const lib = useLibrary();
  const route = useRoute();
  const acc = useAccount();

  useEffect(() => {
    void load().then(initAccount);
    // Plain URLs for the legal pages (Google's consent screen links to them).
    if (location.pathname === '/privacy' || location.pathname === '/terms' || location.pathname === '/delete-account') {
      history.replaceState(null, '', `/#${location.pathname}`);
    }
    // PWA share target / Android share: /share?title=&text=&url=
    // First visit: sign in or later (not when opened for a link, a legal page or a shared item).
    if (!welcomed() && !acc.user && location.pathname === '/' && (!location.hash || location.hash === '#/')) go('/welcome', true);
    if (location.pathname === '/share') {
      const q = new URLSearchParams(location.search);
      history.replaceState(null, '', '/');
      // A chords page, a YouTube / YouTube Music / Spotify link: find the chords.
      const link = q.get('url') || q.get('text')?.match(/https?:\/\/\S+/)?.[0];
      const kind = link ? linkKind(link) : null;
      // TikTok and Instagram videos: listen to them and work out the chords.
      if (link && (kind === 'tiktok' || kind === 'instagram')) go(`/analyze?url=${encodeURIComponent(link)}`, true);
      else if (link && kind) go(`/find?url=${encodeURIComponent(link)}`, true);
      else go(`/import?${q.toString()}`, true);
    }
  }, []);
  useEffect(() => {
    document.documentElement.lang = st.lang;
    if (st.theme === 'auto') delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = st.theme;
  }, [st.lang, st.theme]);
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [route.join('/')]);

  const [page, a, b] = route;
  const stage = page === 'stage';
  let body: React.ReactNode;
  const song = (id?: string) => lib.songs.find((s) => s.id === id);
  if (page === 'song' && song(a)) body = <SongView key={a} song={song(a)!} />;
  else if (page === 'edit' && song(a)) body = <Editor song={song(a)!} />;
  else if (page === 'import') body = <Import />;
  else if (page === 'find') body = <Find key={location.hash} />;
  else if (page === 'analyze') body = <Analyze key={location.hash} />;
  else if (page === 'profile') body = <ProfilePage />;
  else if (page === 'learn') body = <LearnPage />;
  else if (page === 'course' && a) body = <CoursePage id={a} />;
  else if (page === 'lesson' && a) body = <LessonPage key={location.hash} id={decodeURIComponent(a)} />;
  else if (page === 'learnsong' && song(a)) body = <SongLearn song={song(a)!} />;
  else if (page === 'welcome') body = <Welcome />;
  else if (page === 'onboarding') body = <Onboarding />;
  else if (page === 'privacy' || page === 'terms') body = <Legal doc={page} />;
  else if (page === 'delete-account') body = <Legal doc="delete" />;
  else if (page === 'tuner') body = <Tuner />;
  else if (page === 'metronome') body = <MetronomeView />;
  else if (page === 'listen') body = <Listen />;
  else if (page === 'chords') body = <Dictionary />;
  else if (page === 'setlist' && lib.setlists.find((l) => l.id === a)) body = <Setlist list={lib.setlists.find((l) => l.id === a)!} />;
  else if (stage && a === 'song' && song(b)) body = <SongView key={b} song={song(b)!} stage />;
  else if (stage) {
    const list = lib.setlists.find((l) => l.id === a);
    const ids = list?.songs.filter((id) => song(id)) ?? [];
    const i = Math.min(Number(b) || 0, ids.length - 1);
    if (list && ids.length) body = <SongView key={ids[i]} song={song(ids[i])!} stage setlist={{ id: list.id, index: i, ids }} />;
  }
  if (!body) body = lib.loaded || !page ? <Home /> : null;

  const toggleTheme = () => {
    const dark = st.theme === 'dark' || (st.theme === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
    setSettings({ theme: dark ? 'light' : 'dark' });
  };

  return (
    <div className={`app${stage ? ' is-stage' : ''}`}>
      {!stage && (
        <header className="top">
          <a className="brand" href="#/">
            <img src="/favicon.svg?v=2" alt="" width="32" height="32" />
            <span>Just-Chords</span>
          </a>
          <nav className="top-actions">
            <a className="nav-link learn-link" href="#/learn">
              🎓 {t('learn')}
            </a>
            <a className="nav-link hide-sm" href="https://www.justapps.it">
              JustApps
            </a>
            <button className="icon-btn" onClick={() => setSettings({ lang: st.lang === 'it' ? 'en' : 'it' })} aria-label={t('language')} title={t('language')}>
              <span className="lang">{st.lang === 'it' ? 'EN' : 'IT'}</span>
            </button>
            <button className="icon-btn" onClick={toggleTheme} aria-label={t('theme')} title={t('theme')}>
              <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z" fill="currentColor" />
              </svg>
            </button>
            <a className="icon-btn account-btn" href="#/profile" aria-label={t('profile')} title={acc.user?.name ?? t('profile')}>
              {acc.user?.picture ? (
                <img src={acc.user.picture} alt="" width="28" height="28" referrerPolicy="no-referrer" />
              ) : (
                <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
                  <path fill="currentColor" d="M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-4.4 0-9 2.2-9 5v3h18v-3c0-2.8-4.6-5-9-5z" />
                </svg>
              )}
            </a>
            <a className="icon-btn hide-app" href={REPO} target="_blank" rel="noreferrer" aria-label="GitHub" title="GitHub">
              <svg width="18" height="18" viewBox="0 0 16 16" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"
                />
              </svg>
            </a>
          </nav>
        </header>
      )}
      <main>{body}</main>
      {!stage && (
        <footer className="foot">
          <span>
            <a href="https://www.ticly.it" className="ticly-x">
              Just × <span className="ticly">ticly</span>
            </a> · Just-Chords · <a href="https://www.justapps.it">JustApps</a> · <a href={REPO}>GPL-3.0</a> · <a href="#/privacy">Privacy</a> · <a href="#/terms">{t('terms')}</a> ·{' '}
            <a href="mailto:info@ticly.it">info@ticly.it</a>
          </span>
        </footer>
      )}
    </div>
  );
}
