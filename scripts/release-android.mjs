#!/usr/bin/env node
// One-command Android release:
//   node scripts/release-android.mjs [patch|minor|major|x.y.z]
//
// Bumps the version, runs the tests, builds and signs the APK, puts it in
// android/release/, commits and pushes. The "Android release" workflow then
// publishes it as the GitHub release android-v<version>, and this script
// waits until the download works and checks its SHA-256. The site reads the
// latest release from the GitHub API, so nothing else needs updating.
//
// Signing: JUSTCLIP_KEYSTORE, JUSTCLIP_KEYSTORE_PASSWORD (and optionally
// JUSTCLIP_KEY_ALIAS) in ~/.gradle/gradle.properties or the environment.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const android = join(root, 'android');
const gradleFile = join(android, 'app', 'build.gradle.kts');
const releaseApk = join(android, 'release', 'JustChords.apk');
const REPO = 'justapps-it/Just-Chords';

const run = (cmd, args, opts = {}) => execFileSync(cmd, args, { stdio: 'inherit', ...opts });
const out = (cmd, args, opts = {}) => execFileSync(cmd, args, { encoding: 'utf8', ...opts }).trim();
const die = (msg) => {
  console.error(`\n✗ ${msg}`);
  process.exit(1);
};

// ---- signing configured? ------------------------------------------------------
const props = existsSync(join(homedir(), '.gradle', 'gradle.properties')) ? readFileSync(join(homedir(), '.gradle', 'gradle.properties'), 'utf8') : '';
const keystore = process.env.JUSTCHORDS_KEYSTORE ?? process.env.JUSTCLIP_KEYSTORE ?? props.match(/^JUST(?:CHORDS|CLIP)_KEYSTORE=(.+)$/m)?.[1];
if (!keystore || !existsSync(keystore)) {
  die('Signing key not found. Put justchords-release.jks somewhere safe and add JUSTCLIP_KEYSTORE / JUSTCLIP_KEYSTORE_PASSWORD to ~/.gradle/gradle.properties (see signing.properties).');
}
if (out('git', ['status', '--porcelain'], { cwd: root })) die('Commit or stash your changes first.');

// ---- version bump -----------------------------------------------------------------
const gradle = readFileSync(gradleFile, 'utf8');
const code = Number(gradle.match(/versionCode = (\d+)/)[1]);
const name = gradle.match(/versionName = "([^"]+)"/)[1];
const arg = process.argv[2] ?? 'patch';
let [maj, min, pat] = name.split('.').map(Number);
let next;
if (/^\d+\.\d+\.\d+$/.test(arg)) next = arg;
else if (arg === 'major') next = `${maj + 1}.0.0`;
else if (arg === 'minor') next = `${maj}.${min + 1}.0`;
else if (arg === 'patch') next = `${maj}.${min}.${pat + 1}`;
else die(`Unknown version "${arg}"`);
console.log(`→ Just Chords Android ${name} (${code}) → ${next} (${code + 1})`);
writeFileSync(gradleFile, gradle.replace(/versionCode = \d+/, `versionCode = ${code + 1}`).replace(/versionName = "[^"]+"/, `versionName = "${next}"`));

// ---- test + build -------------------------------------------------------------------
const env = { ...process.env, ANDROID_HOME: process.env.ANDROID_HOME ?? join(homedir(), 'android-sdk') };
if (!existsSync(join(android, 'local.properties'))) writeFileSync(join(android, 'local.properties'), `sdk.dir=${env.ANDROID_HOME}\n`);
try {
  // The app is native; its texts come from the web app and must match it.
  run('npm', ['test'], { cwd: root });
  run('node', ['scripts/gen-android-strings.mjs'], { cwd: root });
  if (out('git', ['status', '--porcelain', 'android/app/src/main'], { cwd: root })) throw new Error('texts out of date: commit the regenerated I18n.kt / LegalText.kt');
  run('./gradlew', [':app:testDebugUnitTest', ':app:assembleRelease', '--no-daemon', '-q'], { cwd: android, env });
} catch {
  writeFileSync(gradleFile, gradle);
  die('Build or tests failed: version left unchanged.');
}
const built = join(android, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
copyFileSync(built, releaseApk);
const sha = createHash('sha256').update(readFileSync(releaseApk)).digest('hex');
const size = statSync(releaseApk).size;
console.log(`✓ APK ${(size / 1048576).toFixed(1)} MB · SHA-256 ${sha}`);


// ---- commit + push → the workflow publishes the release -----------------------------
const branch = out('git', ['rev-parse', '--abbrev-ref', 'HEAD'], { cwd: root });
run('git', ['add', gradleFile, releaseApk], { cwd: root });
run('git', ['commit', '-q', '-m', `Just Chords Android ${next}`], { cwd: root });
run('git', ['push', '-q', 'origin', branch], { cwd: root });
console.log(`✓ Pushed ${branch}: GitHub Actions is publishing android-v${next}…`);

// ---- wait for the release and verify the download ----------------------------------
const url = `https://github.com/${REPO}/releases/download/android-v${next}/JustChords.apk`;
const deadline = Date.now() + 10 * 60_000;
for (;;) {
  // curl honors the system proxy settings, Node's fetch doesn't.
  const tmp = join(tmpdir(), `justchords-${next}.apk`);
  let status = '';
  try {
    status = out('curl', ['-sL', '-o', tmp, '-w', '%{http_code}', url]);
  } catch {
    /* retry */
  }
  if (status === '200') {
    const got = createHash('sha256').update(readFileSync(tmp)).digest('hex');
    if (got !== sha) die(`Downloaded APK checksum ${got} does not match ${sha}`);
    console.log(`✓ Released: https://github.com/${REPO}/releases/tag/android-v${next}`);
    console.log('  chords.justapps.it shows the new version automatically.');
    break;
  }
  if (Date.now() > deadline) die(`Release not available after 10 minutes: check https://github.com/${REPO}/actions`);
  await new Promise((r) => setTimeout(r, 10_000));
}
