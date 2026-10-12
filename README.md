# Scrobbled

An Android client for [ScrobblingService](https://git.daubney.dev/matt/Scrobbler), a self-hosted,
Last.fm-compatible scrobbling server. With it you can:

- **Listen** to the room, identify the song with [ACRCloud](https://www.acrcloud.com), edit the result, then scrobble it or set it as now playing.
- **Add** scrobbles by searching [MusicBrainz](https://musicbrainz.org) or typing them in, choosing when they were played.
- **See** what you're listening to now, your totals, and your scrobble history.
- **Scrobble offline**: scrobbles made without a connection are saved on the device and sent automatically later.

The app follows the system light/dark setting and uses the Scrobbled brand palette, wordmark and Bricolage Grotesque typeface from [ScrobbledDesign](https://git.daubney.dev/matt/ScrobbledDesign) (artwork licence and font licence in `design/`).

## Requirements

| What | Version |
|---|---|
| Android device or emulator | Android 10 (API 29) or newer |
| JDK | 17 or newer (Android Studio's bundled JDK works) |
| Android SDK | platform 37 (Gradle downloads missing pieces if licences are accepted) |
| Gradle / AGP | wrapper-managed (Gradle 9.6.0, AGP 9.4.1, Kotlin 2.3.x) |

You also need a running ScrobblingService server and, for real music recognition, an ACRCloud account (see below).

## Build

Clone, then build from the project root:

```sh
./gradlew assembleDebug          # debug APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android lint
./gradlew installDebug           # build and install on the connected device/emulator
```

Or open the folder in Android Studio and press Run.

The macOS system Java may be too old (1.8). If `./gradlew` complains, point it at a newer JDK, for example
Android Studio's:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

With more than one device attached, choose one with `ANDROID_SERIAL=<serial> ./gradlew installDebug`
(`adb devices` lists serials).

### Continuous integration

`.gitea/workflows/ci.yml` runs unit tests, lint and a debug build on every push and pull request. It needs a runner with
the `ubuntu-latest` label that can fetch Temurin 21 and the Android SDK.

## First run

1. Install and open the app.
2. Enter the **server address** of your ScrobblingService, your **username** and **password**, then tap **Connect**.
   - A missing scheme defaults to `https://`. Plain `http://` is allowed because self-hosted servers are often on a LAN.
   - On connect the app logs in, finds or creates an API app called "Scrobbled Android" on the server, and obtains a Last.fm
     session key for scrobbling. Your password is not stored.
3. Use the **Add** tab right away. **Listen** runs in demo mode until you add ACRCloud keys (next section).

### Reaching a server running on your computer

- **Emulator:** `http://10.0.2.2:<port>` can time out on some emulator images. This is reliable instead:
  ```sh
  adb reverse tcp:5050 tcp:5050
  ```
  then use `http://localhost:5050` as the server address.
- **Physical phone over USB:** the same `adb reverse` works (add `-s <serial>` if several devices are connected).
- **Physical phone on Wi-Fi:** use the computer's LAN address and make sure the server listens on it.

To run the server locally, follow the ScrobblingService docs. With Docker it is `docker compose up -d --build api` in that
repository (the API listens on port 5050).

## Setting up ACRCloud (music recognition)

Listen uses ACRCloud's identification API. Until keys are saved, Listen shows a **Demo mode** banner and returns
made-up songs so the flow can still be tried. ACRCloud's console and pricing change over time, so check their site for current
details.

1. Create an account at <https://console.acrcloud.com> and verify your email. New accounts normally get a time-limited free trial.
2. Choose a **region** when asked. It decides your host (for example `identify-eu-west-1.acrcloud.com`).
3. Go to **Audio & Video Recognition** and **Create Project**:
   - give it a name, such as "Scrobbled";
   - set the audio source to **recorded audio** (microphone input);
   - attach the **ACRCloud Music** bucket, their ready-made commercial music database (without a bucket there is nothing to match against).
4. From the project's page, copy the **Host**, **Access Key** and **Access Secret**.
5. In the app, open **Home → Settings → Music recognition**, enter the three values and tap **Save keys**. Saved keys show
   "Listen uses ACRCloud" and the demo banner disappears. Saving all three fields empty switches back to demo mode.

Notes:

- The **access secret is private**. Don't commit it or paste it into chats or issues. The app stores the keys encrypted with an
  Android Keystore key and never writes them to the repository.
- "ACRCloud rejected your keys" usually means a wrong host region, a stray space, or the project has no music bucket attached.
- Results can lack a MusicBrainz ID and sometimes use an odd album name (for example a remaster title). The confirmation
  screen lets you correct both before scrobbling.
- For testing, use a real device with music playing a metre or so from the speaker. Emulator microphones only provide silence
  or host audio.

## Permissions and settings

| Setting | Why |
|---|---|
| Internet | talks to your server, MusicBrainz and ACRCloud |
| Microphone (`RECORD_AUDIO`) | Listen; requested the first time you tap Listen. The microphone is optional hardware, so the app installs without it |
| Plain-HTTP traffic allowed | self-hosted servers are frequently HTTP (`res/xml/network_security_config.xml`) |

**Settings screen:** the signed-in account and server, **Sign out**, and the ACRCloud keys. Signing out while scrobbles are
waiting to send asks first, because those scrobbles belong to the account and are discarded on sign-out.

**Session expiry:** the server's login token lasts 30 days and cannot be refreshed. When it lapses the app returns to the
sign-in screen with the server and username filled in, and queued scrobbles are sent after you sign back in.

**MusicBrainz:** requests are limited to one per second and identify the app in the User-Agent by its repository URL
(`MusicBrainzSearch.USER_AGENT`). Change that if you fork the project.

## How scrobbles are sent

- Scrobbles use the server's Last.fm-compatible `/2.0/` API, signed with the app's API secret and a session key.
- Reading history and now-playing uses the server's `/api/v1` API with a bearer token.
- If a send fails for a temporary reason (no connection, bad gateway, server error, rate limit) the scrobble is stored in a local
  database and a WorkManager job retries when the network is available, oldest first, 50 at a time. The server ignores
  duplicates, so retrying is safe. Plays older than the server's 14-day limit are dropped.
- A scrobble made through Listen records the time listening **started**, not when you confirmed it.

## Project layout

```
app/src/main/java/dev/daubney/scrobbled/
  data/api/          Last.fm (/2.0/) client with request signing; /api/v1 client
  data/auth/         login, credential storage (Android Keystore)
  data/history/      now playing, recent scrobbles, summary
  data/scrobble/     TrackDraft (the editable track) and scrobble submission
  data/queue/        offline queue (Room) and WorkManager flush
  data/search/       MusicBrainz search
  data/recognition/  microphone recorder, ACRCloud recognizer, demo recognizer, key storage
  ui/                Compose screens and ViewModels (home, history, add, listen, settings, connect), theme
```

Unit tests live under `app/src/test` and run on the JVM with no device (`./gradlew testDebugUnitTest`). Network code is tested
against `MockWebServer`.

## Troubleshooting

- **`Unable to connect` / timeouts on an emulator:** use `adb reverse` as described above.
- **Blank or black screen right after reinstalling on an emulator:** wait about 20 seconds for Google Play's package scan, then
  force-stop and relaunch the app.
- **"Your session has expired":** sign in again; the server and username are pre-filled.
- **Build fails with a Java version error:** set `JAVA_HOME` to a JDK 17+ (see Build).
