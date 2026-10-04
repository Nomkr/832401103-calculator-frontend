# Android Code Style

This project follows the Android Java style conventions and the main
rules of the Google Java Style Guide:

- 4-space indentation; classes in PascalCase, methods and variables in
  camelCase, constants in UPPER_SNAKE_CASE.
- One clear responsibility per method; network requests run on background
  threads and UI updates run on the main thread.
- All `HttpURLConnection` objects and streams are closed, with connect and
  read timeouts set.
- User-facing error messages are short; details go to `Log.e`.
- Calculation logic is never duplicated on the client; results only come
  from the backend API.
- Keep resource IDs stable when editing layouts.

References:

- https://google.github.io/styleguide/javaguide.html
- https://developer.android.com/kotlin/style-guide
