# Just Chords

Chords, tuner, metronome and stage mode in one open-source app, by [JustApps](https://www.justapps.it).
Live at **https://chords.justapps.it**, Android APK in the [releases](https://github.com/justapps-it/Just-Chords/releases).

- **Find chords**: search a song and get the versions on Ultimate Guitar and Accordi e Spartiti (rating, votes, level); the one you pick is opened and saved with a link to its source. The Android app downloads the page from the phone itself; the web app goes through the Worker. Pasting text or a ChordPro file still works: the importer understands chords-over-lyrics, Ultimate Guitar `[ch]` markup, Italian notation (DO, RE, LAm, SOL7…) and ChordPro.
- **Links**: paste or share a Spotify, YouTube or YouTube Music link to get the chords of that song.
- **Learn**: courses for beginners, intermediate and advanced players (first chords, rhythm, melodies, barre chords, riffs and blues, colour chords, fingerstyle, scales). Every lesson plays slowly first and speeds up to the target tempo, with a click, strumming arrows, a tab that follows the beat, plucked notes to listen to and the microphone checking your chords. Every song has a **Learn** button: its chords (easier shapes and a capo for beginners), the hardest switches, the rhythm, riffs from its tabs, then playing along with the original, slowed down first (YouTube on the web, the audio fetched by the phone on Android).
- **Accounts**: Google or email + password. New accounts get a short, skippable level test (questions and chords heard through the microphone) and can paste a Spotify, YouTube or Deezer playlist to seed their taste profile.
- **Analyse a video**: send a YouTube, TikTok or Instagram video (or an audio/video file) and Just Chords analyses its audio, then tells you how to play it: chords over time, key, tempo, the capo that gives the easiest shapes, a strumming pattern for the tempo, the chords to practise. With synced lyrics on, Whisper (Cloudflare Workers AI) transcribes the singing with word timestamps and the chords land on the right syllables. The Android app fetches just the audio straight from the link on the phone (YouTube through [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), TikTok and Instagram from their public pages), analyses it and deletes it; no screen recording, nothing goes through our server. If a link can't be fetched it can still hear the video playing in its official player through Android's audio playback capture. The website uses a file, the tab's audio (Chrome desktop) or the microphone, since browsers can't read those pages.
- **Listen while you play**: a YouTube player next to the sheet (found automatically with a YouTube Data API key, or from a pasted link).
- **Levels**: every song gets a difficulty (easy / intermediate / advanced) from its chords with the best capo, barre chords, how many chords and how often they change.
- **For you**: recommendations from a taste profile (genres, level, artists learnt from what you import, open, star, 👍/👎), related artists and genre seeds from Deezer, and a curated list of songs by level.
- **Google account (optional)**: library, setlists and profile synced across devices.
- **Interactive sheet**: transpose, capo (with a suggested capo for easier shapes), simplify chords, C D E or Do Re Mi notation, font size, autoscroll.
- **Chord diagrams for any chord**: guitar (any tuning), ukulele and piano, generated from music theory, several voicings each, left-handed mode.
- **Stage mode**: setlists, full screen, autoscroll, Bluetooth page-turner pedals (↓/Space/PageDown scroll, →/← next/previous song), screen kept on.
- **Audio, all on your device (WebAssembly on the web, native code in the app)**: live chord recognition from the microphone, chord recognition from an audio file (chroma + Viterbi), chromatic tuner (YIN) with alternate tunings, sample-accurate metronome with tap tempo.
- Songs are stored on your device (IndexedDB on the web, a JSON file in the app), and in your account when signed in. Offline PWA. No ads.

## Backend

`src/worker/index.ts` is a Cloudflare Worker in front of the static app: `/api/search`, `/api/song`, `/api/link`, `/api/yt`, `/api/recs`, `/api/auth/google`, `/api/me`, `/api/profile`, `/api/sync`, `/api/transcribe` (≤ 35 s WAV clips → words with timestamps, Whisper on Workers AI, 20 minutes per IP per day). Accounts and libraries are in the `USERS` KV namespace. Configuration:

- `GOOGLE_CLIENT_ID` (var, in `wrangler.jsonc`): OAuth web client. Authorized JavaScript origin: `https://chords.justapps.it`.
- `SESSION_SECRET` (secret): signs session tokens. `openssl rand -hex 32 | npx wrangler secret put SESSION_SECRET`
- `AI` (Workers AI binding): Whisper for synced lyrics.
- `YOUTUBE_API_KEY` (secret, optional): YouTube Data API v3 key to find the video of a song automatically.
- Android: an Android OAuth client for `it.justapps.justchords` with the signing certificate SHA-1, in the same Google Cloud project.

`npm run deploy` uses Workers Assets; `npm run deploy:bundle` packs the app into the Worker script instead (for environments that can't use the assets upload).

## Development

```bash
npm install
npm run dev       # dev server
npm test          # unit tests (music theory, parser, voicings, WASM DSP)
npm run build     # AssemblyScript → dsp.wasm, type check, Vite build
npm run deploy    # Cloudflare Workers static assets (chords.justapps.it)
node scripts/e2e.mjs http://localhost:4173   # browser end-to-end test
```

The DSP is written in AssemblyScript (`assembly/index.ts`) and compiled to `public/dsp.wasm`.

## Android app

`android/` is a native app (Kotlin, Jetpack Compose, Material 3 with the JustApps look, full screen). It does everything the website does, with the same logic and the same API, so library, setlists and taste profile sync between the two:

- `core/`: music theory, ChordPro and import parser, voicings, difficulty, taste profile, song-page parsers and the DSP (chroma, YIN, Viterbi), ported from `src/lib` and `src/audio` and tested against the same cases (`CoreTest`).
- `data/`: the library as JSON in the web app's format, settings, the API client and the Google account (Credential Manager, 14+ consent, sync, export, delete).
- `ui/`: the screens. Texts and legal pages are generated from the web app with `node scripts/gen-android-strings.mjs`.
- `audio/`: microphone (AudioRecord), metronome (AudioTrack), audio file decoding (MediaCodec).

```bash
cd android
./gradlew testDebugUnitTest          # core tests + screen rendering
./gradlew recordRoborazziDebug       # screenshots in app/build/outputs/roborazzi
npm run release:android -- patch     # from the root: build, sign, commit, publish
```

## License

GPL-3.0-or-later.
