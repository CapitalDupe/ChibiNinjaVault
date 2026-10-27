# Ninja Run — Encrypted Media Vault

A Kotlin + Jetpack Compose Android app: a lightweight 3-lane endless runner
("Ninja Run") that doubles as the entry screen to your own encrypted personal
media vault. Swipe left/right to change lanes, up to jump, down to slide — and
if you swipe your own configured pattern, the vault unlocks.

This is a **user-owned, user-configured** access mechanism. It does not hide
anything from the device, evade monitoring, or include any developer backdoor
— if you lose your pattern/PIN, the vault is not recoverable, by design.

## What's implemented

- **Setup wizard** — choose a 3–8 swipe unlock pattern, confirm it twice,
  optionally add a PIN and/or biometric unlock.
- **Ninja Run game screen** — endless runner with lanes, obstacles (jump to
  clear them), coins, score, and swipe capture feeding the unlock-sequence
  validator. Failed unlock attempts trigger a persisted lockout with
  exponential backoff (it survives restarts); ordinary gameplay swipes don't
  count as attempts.
- **Encrypted vault** — AES-256-GCM envelope encryption: each file gets its
  own random key, which is itself wrapped by a hardware-backed Android
  Keystore master key. No keys are hard-coded anywhere.
- **File management** — import via the system document picker (no broad
  storage permission needed), search, delete, rename, multi-select (long-press),
  export (decrypt back out on request). Errors are reported in the UI.
- **Preview** — audio/video via Media3/ExoPlayer and images in an image
  viewer. Content is decrypted to a private cache temp file for the duration of
  viewing only and deleted afterwards (also swept on next launch after a
  crash). Other file types can be exported.
- **Settings** (gear icon in the vault toolbar) — change
  pattern/PIN/biometrics, auto-lock timeout, lock-on-background, haptics,
  theme, and a vault reset that deletes every encrypted file and the Keystore
  key (irreversible).
- **App lock** — the vault locks on backgrounding (or after the auto-lock
  timeout) and after inactivity, and the app always starts on the game screen
  after the process is killed. PIN entry is rate-limited with persisted backoff.
- **Unit tests** — activation sequence validation & persisted lockout, app-lock
  rules, PIN hashing (PBKDF2, salted, never stores plaintext), and AES-GCM
  stream encrypt/decrypt round-trips + tamper/wrong-key detection.

## Building the APK from GitHub on mobile (no Android Studio needed)

This repo includes `.github/workflows/android.yml` ("Android CI"), which runs
the unit tests and builds a debug APK on every push and pull request to `main`
using GitHub's hosted runners.

1. Create a new **empty** repository on GitHub (don't initialize with a
   README, so there's no merge conflict).
2. Push this project to it. From a terminal (e.g. Termux, or GitHub's mobile
   web code editor's "Add file → Upload files" if you don't have shell
   access):
   ```bash
   cd ChibiNinjaVault
   git init
   git add .
   git commit -m "Initial Ninja Run vault app"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
   If you're doing this purely through the GitHub mobile app/website without a
   terminal: create the repo, then use the web UI's "Add file → Upload files"
   to drag in the whole folder (GitHub's mobile web UI supports multi-file
   upload of a zip's extracted contents in recent versions; if it doesn't
   accept a full nested folder tree in one go, Termux + git is the most
   reliable path on Android).
3. Go to the **Actions** tab of your repo on GitHub (works fine in the mobile
   app or mobile browser).
4. You should see an "Android CI" run start automatically. Tap it.
5. When it finishes (green check), scroll down to **Artifacts** and download
   `ChibiNinjaVault-debug-apk` — that's a zip containing `app-debug.apk`.
6. Unzip on your phone (most Android file managers can do this, or use an
   app like Files by Google) and tap the `.apk` to install. You'll need to
   allow "install unknown apps" for whichever app you used to open it.

If a run ever fails, tap into the failed step in the Actions log — it'll show
the Gradle error directly, and that log is exactly what you'd want to share if
you ask for help debugging it.

### Manually triggering a build without a new push

The workflow also supports `workflow_dispatch`, so once it's pushed once, you
can go to **Actions → Android CI → Run workflow** any time you want
a fresh APK without changing code.

## Exporting encrypted files and keys

[#exporting-encrypted-files-and-keys](#exporting-encrypted-files-and-keys)

From a file's menu in the vault:

- **Export decrypted copy** - the original file, readable anywhere.
- **Export encrypted copy (.adx)** - the raw encrypted blob. It cannot be opened without its key.
- **Export key...** - writes that file's data key to a `.adxkey` file, locked with a password you choose (8+ characters). The password cannot be recovered.

To bring a file back in (on this or another phone), tap the key icon in the vault toolbar, pick the `.adx` file, then its `.adxkey` file, and enter the password. The key is checked against the file before anything is stored, then re-protected with that device's Keystore key.

The Keystore master key itself can never be exported (it is hardware-backed and non-extractable). Only per-file keys can be exported, and only encrypted under your password.

Formats, for anyone writing their own decryptor:

- `.adx`: 12-byte IV, then AES-256-GCM ciphertext with a 16-byte tag (no additional data).
- `.adxkey`: text lines `ADXKEY1`, `iter`, `salt`, `iv`, `name`, `mime`, `key` (Base64). The key is AES-256-GCM encrypted under PBKDF2-HMAC-SHA256(password, salt, iter); the AAD is `ADXKEY1|<name>|<mime>`.

## Security notes / limitations

- No plaintext file ever touches shared/public storage. Decrypted bytes only
  exist in the app's private cache dir while a file is being viewed and are
  deleted when viewing ends, when the vault locks, and on next launch.
- The Keystore master key never leaves the Keystore; only wrapped (encrypted)
  per-file keys are stored on disk/in the database.
- **The swipe pattern, PIN and biometrics are an access gate inside the app,
  not encryption keys.** File keys are protected by the Keystore master key
  alone, so these factors do not add cryptographic protection against someone
  who can run code as the app (e.g. a rooted device). Biometric unlock is not
  bound to a crypto key either.
- The swipe pattern has a small search space (3-8 swipes over 4 directions);
  the persisted lockout slows guessing, and a PIN adds a second factor with
  its own rate limit. Lockout timing uses the wall clock, so changing the
  device clock can shorten it.
- PIN is hashed with salted PBKDF2 (120k iterations) — plaintext PIN is never
  persisted.
- Resetting the vault deletes all encrypted files and destroys the Keystore
  key, after which any leftover ciphertext can't be decrypted. There is
  intentionally no recovery backdoor.
- `FLAG_SECURE` is set on the whole activity (no screenshots / blank recents
  thumbnail).
- Depending on the Android version, the platform's AES-GCM may buffer an entire
  file in memory while encrypting/decrypting, so very large files can use a
  lot of RAM. Chunked streaming AEAD would remove that limit.
- This is a working scaffold, not a fully hardened production app. Still
  missing: instrumented (on-device) tests for the real Keystore wrap/unwrap
  path (unit tests cover the streaming cipher logic, PIN hashing, lockout and
  app-lock rules on the plain JVM), a signed release build, and upgrading the
  alpha `security-crypto`/`biometric` dependencies.
