// Google Play graphics from the app's screenshot tests (Roborazzi):
//   node scripts/store-graphics.cjs   → store/feature-graphic.png, store/screenshots/<lang>/NN.png
// Run the screenshot tests first:
//   cd android && ./gradlew :app:testDebugUnitTest --tests '*ScreensTest*' -Proborazzi.test.record=true
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const shots = path.join(root, 'android/app/build/outputs/roborazzi');
const out = path.join(root, 'store');
const img = (n) => 'data:image/png;base64,' + fs.readFileSync(path.join(shots, n + '.png')).toString('base64');
const logo = fs.readFileSync(path.join(root, 'public/favicon.svg'), 'utf8');

const SCREENS = [
  ['song', { it: ['Tutti gli <b>accordi</b>', 'Trova la canzone, trasponi, scegli il capotasto.'], en: ['Every <b>chord</b> you need', 'Find the song, transpose, pick the capo.'] }],
  ['learn', { it: ['<b>Impara</b> al tuo livello', 'Corsi dal primo accordo agli assoli.'], en: ['<b>Learn</b> at your level', 'Courses from your first chord to solos.'] }],
  ['lesson-switch', { it: ['Parti <b>piano</b>, poi a tempo', 'Metronomo, pennata e cambi guidati.'], en: ['Start <b>slow</b>, then on tempo', 'Click, strumming and guided changes.'] }],
  ['song-learn', { it: ['Ogni canzone è una <b>lezione</b>', 'Accordi facili, cambi, ritmo e riff.'], en: ['Every song is a <b>lesson</b>', 'Easy shapes, changes, rhythm and riffs.'] }],
  ['tuner', { it: ['<b>Accordatore</b> e metronomo', 'Precisi, sempre a portata di mano.'], en: ['<b>Tuner</b> and metronome', 'Accurate and always at hand.'] }],
  ['song-dark', { it: ['Pronto per il <b>palco</b>', 'Scorrimento automatico e tema scuro.'], en: ['Ready for the <b>stage</b>', 'Autoscroll and dark theme.'] }],
];

const css = `
  * { margin: 0; box-sizing: border-box; }
  body { font-family: Inter, sans-serif; background: #0f0f10; color: #f5f5f7; }
  .glow { position: absolute; border-radius: 50%; filter: blur(120px); background: #ff5a1f; opacity: .28; }
  b { color: #ff5a1f; font-weight: 800; }
  .phone { position: absolute; border-radius: 56px; overflow: hidden; border: 10px solid #232325; box-shadow: 0 40px 80px rgba(0,0,0,.55); background: #fff; }
  .phone img { width: 100%; display: block; }
`;

function screenHtml(file, [title, sub]) {
  return `<html><head><style>${css}
    .s { position: relative; width: 1080px; height: 1920px; overflow: hidden; }
    h1 { position: absolute; top: 120px; left: 80px; right: 80px; font-size: 88px; line-height: 1.05; font-weight: 800; letter-spacing: -2px; }
    p { position: absolute; top: 340px; left: 80px; right: 80px; font-size: 40px; color: #a1a1a6; }
    .phone { left: 150px; top: 470px; width: 780px; height: 1600px; }
  </style></head><body><div class="s">
    <div class="glow" style="width:700px;height:700px;left:-200px;top:-250px"></div>
    <div class="glow" style="width:600px;height:600px;right:-250px;bottom:200px;opacity:.18"></div>
    <h1>${title}</h1><p>${sub}</p>
    <div class="phone"><img src="${img(file)}"></div>
  </div></body></html>`;
}

function featureHtml(lang) {
  const tag = lang === 'it' ? 'Accordi, lezioni e accordatore.<br>Impara a suonare, al tuo ritmo.' : 'Chords, lessons and tuner.<br>Learn to play, at your pace.';
  return `<html><head><style>${css}
    .f { position: relative; width: 1024px; height: 500px; overflow: hidden; }
    .logo { position: absolute; left: 70px; top: 110px; width: 96px; height: 96px; border-radius: 22px; overflow: hidden; }
    .logo svg { width: 100%; height: 100%; }
    h1 { position: absolute; left: 70px; top: 230px; font-size: 76px; font-weight: 800; letter-spacing: -2px; }
    p { position: absolute; left: 72px; top: 330px; font-size: 26px; line-height: 1.4; color: #a1a1a6; }
    .by { position: absolute; left: 72px; bottom: 34px; font-size: 18px; color: #6e6e73; font-weight: 700; }
    .phone { width: 300px; height: 640px; border-radius: 40px; border-width: 8px; }
  </style></head><body><div class="f">
    <div class="glow" style="width:520px;height:520px;right:-120px;top:-160px"></div>
    <div class="logo">${logo}</div>
    <h1>Just-Chords</h1><p>${tag}</p>
    <div class="by">ticly × Just</div>
    <div class="phone" style="left:600px;top:60px;transform:rotate(-6deg)"><img src="${img('song-dark')}"></div>
    <div class="phone" style="left:790px;top:110px;transform:rotate(5deg)"><img src="${img('learn')}"></div>
  </div></body></html>`;
}

(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ deviceScaleFactor: 1 });
  for (const lang of ['it', 'en']) {
    fs.mkdirSync(path.join(out, 'screenshots', lang), { recursive: true });
    for (const [i, [file, caps]] of SCREENS.entries()) {
      await p.setViewportSize({ width: 1080, height: 1920 });
      await p.setContent(screenHtml(file, caps[lang]));
      await p.screenshot({ path: path.join(out, 'screenshots', lang, `${String(i + 1).padStart(2, '0')}-${file}.png`) });
    }
    await p.setViewportSize({ width: 1024, height: 500 });
    await p.setContent(featureHtml(lang));
    await p.screenshot({ path: path.join(out, `feature-graphic-${lang}.png`) });
  }
  fs.copyFileSync(path.join(root, 'public/icons/icon-512.png'), path.join(out, 'icon-512.png'));
  await b.close();
  console.log('store graphics ok');
})();
