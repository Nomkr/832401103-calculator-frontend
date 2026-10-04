# Calculator Android Client

Native Android client for the calculator system. The client only handles
input, display and history management; expression parsing, evaluation,
validation and persistence are all done by the Flask backend.

## Tech Stack

- Java 11, Android SDK 35+, min SDK 24 (Android 7.0)
- AndroidX AppCompat, Material Components
- `HttpURLConnection` + JSON HTTP API

## Running

1. Open `calculator_android` in Android Studio.
2. Start `calculator_backend/app.py` and confirm
   `http://127.0.0.1:5000/api/health` returns `{"status":"ok"}`. The
   backend must listen on `0.0.0.0:5000`, which `app.py` does by default.
3. Run the app on an emulator. The client probes `BACKEND_URLS` in
   `MainActivity.java` in order: the dev machine's LAN address first,
   then the standard emulator alias `10.0.2.2:5000`. After changing
   networks, update the first entry to the machine's current IPv4
   address (`ipconfig`).
4. If the Windows Firewall prompt appears, allow Python on private
   networks.

The manifest declares the INTERNET permission and allows cleartext HTTP
for local development. Use HTTPS in production.

## Configuration

- Local backend port defaults to `5000`.
- The SQLite database is owned by the backend; the Android client keeps
  no local copy of history.
- Never put `127.0.0.1` in the Android address list; use the LAN address
  or `10.0.2.2` (emulator alias).

## Production Backend

After deploying the backend, put its HTTPS URL first in `BACKEND_URLS`:

```java
private static final String[] BACKEND_URLS = {
        "https://nomkr.pythonanywhere.com",
        "http://192.168.50.12:5000",
        "http://10.0.2.2:5000"
};
```

## Features

- Arithmetic, parentheses, decimals and unary signs
- Scientific keys: `√` (square root), `x²` (square), `x^y` (power), `%` (percent)
- Backend error messages shown on the display
- History loaded from the database: per-record delete, clear all (with
  confirmation), empty and failure states
- Day/night theme toggle (persisted across launches)

## API Contract

`POST /api/calculate`, `GET /api/history`, `DELETE /api/history/<id>`,
`DELETE /api/history`, `GET /api/health`.

## Build Checks

```bash
./gradlew assembleDebug
./gradlew test
```

`assembleDebug` produces the installable APK; `test` runs the Android
unit tests. Backend tests run separately in `calculator_backend`.

## Demonstration Checklist

1. Open `https://nomkr.pythonanywhere.com/api/health` and confirm the
   response is `{"status":"ok"}`.
2. Install `app/build/outputs/apk/debug/app-debug.apk`, or run the app from
   Android Studio.
3. Demonstrate `1+2*3`, an invalid expression, history loading, single-record
   deletion, clear-all confirmation, and the day/night theme switch.
4. For local development, start the Flask backend first. The client probes
   the deployed HTTPS address, then the LAN and emulator fallback addresses.

See [codestyle.md](codestyle.md) for code style.
