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
- **Ninja Run game screen** — endless runner with lanes, obstacles, coins,
  score, and swipe capture feeding the unlock-sequence validator (with
  lockout/backoff after repeated wrong attempts).
- **Encrypted vault** — AES-256-GCM envelope encryption: each file gets its
  own random key, which is itself wrapped by a hardware-backed Android
  Keystore master key. No keys are hard-coded anywhere.
- **File management** — import via the system document picker (no broad
  storage permission needed), search, delete, rename, multi-select, export
  (decrypt back out on request).
- **Media playback** — Media3/ExoPlayer, decrypts to a private cache temp file
  for the duration of playback only, deleted immediately after.
- **Settings** — change pattern/PIN, toggle biometrics, auto-lock timeout,
  lock-on-background, sound/haptics, theme, and a vault reset (with a clear
  warning that reset is destructive and irreversible).
- **Unit tests** — activation sequence validation & lockout, PIN hashing
  (PBKDF2, salted, never stores plaintext), and AES-GCM stream encrypt/decrypt
  round-trips + tamper/wrong-key detection.

## Building the APK from GitHub on mobile (no Android Studio needed)

This repo includes `.github/workflows/build.yml`, which runs on every push to
`main` and builds a debug APK using GitHub's hosted runners.

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
4. You should see a "Build Ninja Run APK" run start automatically. Tap it.
5. When it finishes (green check), scroll down to **Artifacts** and download
   `ninja-run-debug-apk` — that's a zip containing `app-debug.apk`.
6. Unzip on your phone (most Android file managers can do this, or use an
   app like Files by Google) and tap the `.apk` to install. You'll need to
   allow "install unknown apps" for whichever app you used to open it.

If a run ever fails, tap into the failed step in the Actions log — it'll show
the Gradle error directly, and that log is exactly what you'd want to share if
you ask for help debugging it.

### Manually triggering a build without a new push

The workflow also supports `workflow_dispatch`, so once it's pushed once, you
can go to **Actions → Build Ninja Run APK → Run workflow** any time you want
a fresh APK without changing code.

## Security notes / limitations

- No plaintext file ever touches shared/public storage. Decrypted bytes only
  ever exist in the app's private cache dir, only during active playback, and
  are deleted the moment playback ends.
- The Keystore master key never leaves the Keystore; only wrapped (encrypted)
  per-file keys are stored on disk/in the database.
- PIN is hashed with salted PBKDF2 (120k iterations) — plaintext PIN is never
  persisted.
- There is intentionally no recovery backdoor. Losing your pattern **and**
  PIN means the data is unrecoverable — that's a deliberate security
  trade-off, the same one any real encrypted vault makes.
- This is a solid working scaffold, not a fully hardened production app.
  Before trusting it with sensitive files long-term, you'd want to add: screen-
  recording/screenshot blocking on vault screens (`FLAG_SECURE`), a proper
  app-wide lock-state ViewModel driving auto-lock/lock-on-background instead
  of the current per-screen wiring, and instrumented (on-device) tests for
  the real Keystore wrap/unwrap path (the included unit tests cover the
  streaming cipher logic and PIN hashing, which run on plain JVM; Keystore
  itself only exists on-device).
