# Munawar AI v0.6 (UI 2.0)

Created by **Munawar Uddin** • 📞 03022565427

Android voice assistant (Kotlin, plain Android views, no AndroidX): "Hey Munawar" wake word, Gemini chat + Gemini Live voice,
default model **Gemini 3.5 Flash** (first page); other models (3.6 / 3.7 / 3.8 / Pro / Flash-Lite / 2.5) in **Settings → Chat model**.

## Get the APK (GitHub Actions)
1. Create a GitHub repo and upload the CONTENTS of this zip (so `.github/`, `app/`, `settings.gradle.kts` and `keystore/` sit at the top of the repo). Hidden folders like `.github` are skipped by drag-and-drop on some browsers: use "Add file → Upload files" after unzipping, or `git push`.
2. Open **Actions → Build Munawar AI APK → Run workflow** (it also runs on every push).
3. When it is green, open the run → **Artifacts → MunawarAI-apk** → download, unzip, install `MunawarAI.apk`.
   If the build fails, download the **build-reports** artifact (or open the log) and send me the first red error line.
- Signing: a fixed key (`keystore/munawar-ci.jks`) is bundled so every build has the same signature and updates install over the old app.
  For your own private key add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Tag `v0.5.0` to also publish a GitHub Release with the APK.

## What changed in v0.5
**Fixes**
- Edge lighting: the whole frame was rotating (so it flew off-screen). Now only the colours rotate around the border; added glow strength, palette, thickness, speed.
- Background: wake-word recogniser was never released (mic stayed busy and blocked the session). Fixed; accessibility service now starts the core itself.
- Build: bigger Gradle/Kotlin memory, stable signing key, `--no-daemon`, failure reports uploaded.

**New look (UI 2.0)** – 3D glass cards/buttons with depth, phone-tilt parallax, press-tilt tiles, new 3D orb (glass sphere, orbit rings, sparkles, audio bars, colour-cycling aura).
**Everything customizable**: Settings → 🎨 Appearance (colour theme, custom accent/background hex, text size, corner roundness, card opacity, 3D tilt on/off + strength,
home orb size, tile animation, orb speed/bars/sparkles/rings, orb colour style, edge-light colours/glow/thickness/speed, reset to default).

**MYRA features added** (see ✨ Features screen): SOS with location, answer/reject call, contact lookup, missed calls, OTP on request (masked otherwise),
read/reply/clear notifications, battery info, storage analyzer, clipboard, lock, parking saver, smart modes (driving/sleep/work/game), traffic layer,
**Files & photos** (browse a folder, share, zip, delete; photo cleaner), missions (autonomous multi-step), automation triggers (time, charger, battery %,
headphones, screen, Wi-Fi, app open/close) with speak/run/talk actions, habit learning, website builder, deep research, game coach, phone health,
connectors (GitHub, Telegram, Tavily, Groq/OpenAI/Claude), voice passphrase lock.

## Notes
- This project was edited and statically reviewed, but I could not run Gradle in my environment, so the first CI run is the real compile test.
- Offline maps download is "coming soon" (same as in the MYRA plan).
- Voice lock is a spoken passphrase, not a voice-print.

## v0.6 changes
- PC Connect removed.
- Default theme: dark blue. 20 dark themes + 4 white (light) themes; the ☰ menu has a one-tap white/dark switch.
- Everything animated is OFF by default (orb animation, edge light, tilt, moving background). Turn them on in Settings.
- 50 orb designs (10 colours x 5 forms) + animation Off/Calm/Full; 5 edge-light animation styles.
- Settings is grouped into 5 collapsible categories; every screen is reached from the ☰ menu; Home is just the orb.
- "Allow all permissions" at first start and in Control center, plus a chain for special access (overlay, notification access, accessibility, battery, system settings, Do-Not-Disturb).
- Voice phone control: brightness, auto-rotate, DND, vibrate/ring, Quick-Settings toggles (Wi-Fi, Bluetooth, hotspot, airplane, NFC, location, data), quick settings, power menu, lock, open apps.
- The system overlay is a 1-pixel invisible window unless you enable the full edge light over other apps (touch can never be blocked).

## v0.7 changes
- Vision: "look at my screen …" / "look at camera …" (one-shot), live screen/camera frames to Gemini (Home → 👁 Vision button or Settings), "record video", "selfie".
- Conversation is no longer a phone-call mode: it uses the loud media speaker (volume boost, ducking), keeps listening continuously, auto-reconnects, 24 h wake-lock.
- Wake word: system "ding" silenced (Settings → Sound & wake word).
- App control through Accessibility (now sees all nodes + gestures): skip ad, like, subscribe, comments, comment <text>, forward/rewind, swipes, taps.
- Image generation removed. Every command group has its own switch (Settings → Features on/off).
- GitHub workflow finds the project folder itself and prints compile errors on the run page + uploads build.log.

## v0.8 changes
- Assistant voice is forced to the phone's big built-in speaker (unless headphones / Bluetooth are connected), media volume raised, loudness boost in Settings.
- Gemini Live uses sliding-window context compression so one conversation can run for hours.
- Fixes found in a full read-through: first-run permission screen no longer builds twice, "tap center" gesture no longer swallowed by "click", ad-skip understands filler words ("youtube pe ad skip karo"), removed image generation leftovers.

## v0.9 (CI diagnostics)
The workflow now compiles the Kotlin code in its own step first ("1) Compile Kotlin code"). If there is a code error it is printed as red annotations at the TOP of the run page and as a list in the run Summary, e.g. `e: /home/runner/.../Foo.kt:12:5 Unresolved reference: bar`. Send that line to get it fixed.

## v1.0 – new design, touch fix, autonomous agent
- **Touch fix:** every overlay window now has alpha 0.79. Android 12+ drops touches for all other apps when an overlay of another app is more than 80% opaque (that is why touch only worked inside Munawar and the notification panel).
- **New look ("glass neon"):** glass cards with glowing gradient borders, pill buttons, round ☰ button, animated aurora background, new Home (greeting, live status card, vision chips, mood emoji), new drawer with highlighted current page. UI animation level: Off / Smooth / Full.
- **Autonomous agent:** anything Munawar has no command for is handed to an agent that reads the screen, picks the next tap/type/scroll/swipe/open step with Gemini and repeats (max 18 steps). "do <goal>", "stop task". It refuses payments, passwords, deletes.
- **Greets you by itself** when a conversation starts; has emotions (orb tint + persona); Device-admin permission for instant lock.
- Orb: 80 designs (10 colours x 8 forms). Edge light: 9 animation styles.

## v1.1 – companion mode
- **Tells you by itself:** incoming calls ("Rahul ka call aa raha hai, uthaun?"), missed calls and new messages are announced out loud without asking (Settings → Companion). Needs Phone, Contacts, Call-log and Notification access.
- **Starts conversations itself** after a quiet period (adjustable), plus optional morning / night routines.
- **Different greeting every time** (random style + memory of the last greetings) and **emotion in the voice** (the model is told what to feel; the orb tints and Home shows the mood name).
- **Screen watching:** live screen frames go to Gemini whenever the screen really changes (default on, can be switched off on Home).
- **Never "tap it yourself":** clicks now fall back to a real finger tap, the agent gets coordinates for every item and a screenshot, and retries other ways.
- **Looks after the phone:** skips YouTube ads, taps Allow on permission pop-ups it asked for, keeps slow apps waiting, restarts itself if Android kills it, "fix yourself" self-check.
- Card styles (glass / neon / solid / minimal), Live feed and quick-ask chips on Home.
