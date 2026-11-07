# Net-Tap — Universal HTTP Capture for Android (Xposed)

An Xposed / LSPosed module that captures HTTP(S) traffic from **any Android app** at the application layer first, then emits TLS key material for encrypted transports that cannot be fully decoded in-process. Net-Tap does **not** disable certificate validation or certificate pinning.

| Layer | What it covers |
|---|---|
| **OkHttp 3 / 4** | Stock `okhttp3.*` and apps where OkHttp has been R8-obfuscated or relocated — the installer falls back through four hook strategies until one sticks |
| **Cronet** | Stock `org.chromium.net.impl.CronetUrlRequest` in the canonical Chromium namespace; request + response body closure via `CronetUploadDataStream` and `CronetBidirectionalStream` |
| **gRPC-over-OkHttp** | Hooks `io.grpc.internal.ClientCallImpl.start` / `sendMessage` and wraps the listener with a dynamic proxy so response messages, trailers and status codes are captured alongside the request metadata |
| **`HttpURLConnection`** | Tees `getOutputStream()` / `getInputStream()` / `getErrorStream()` for Firebase, GMS, Facebook SDK, and legacy code paths |
| **Higher-level open clients** | Volley, Fuel, Apache HttpClient 5, Ktor CIO, and AndroidAsync where they bypass the core stacks above |
| **TLS key log** | Conscrypt/JSSE key log plus canonical Cronet/BoringSSL `ssl_key_log_file` injection |

It also tracks per-layer install and capture counts via `MetricsReporter` so you can diagnose "which layer didn't land" on any given host.

Net-Tap keeps vendor-private SDK layers out of the default capture surface. It does not install hooks for app-specific API clients, private Retrofit-style stacks, or vendor-prefixed Cronet packages.

## What it captures per request

* Method, URL, duration
* Request + response headers captured verbatim, **including `authorization` / `cookie` / `x-api-key`** — no redaction is performed. The capture file sits under the host app's private data dir; anyone with root can read it, so treat it as sensitive material.
* Request + response body up to `CaptureConfig.MAX_BODY_BYTES`, truncation flagged explicitly
* Response status code + message
* gRPC method name (`/package.Service/Method`), authority, status code, description

## Output

* JSON Lines at `/data/data/<pkg>/files/_okhttp_capture.jsonl` — one file per hooked app. Authoritative capture source (logcat drops chunks on large payloads, this file does not). When a record is written, bodies are captured up to `CaptureConfig.MAX_BODY_BYTES`; oversized or unsupported bodies (one-shot, duplex, opaque binary, unknown content type) are marked truncated or omitted with an explicit reason. Under sustained file-writer backpressure the oldest pending records may be dropped — watch for `filelogger backpressure: N lines dropped so far` in logcat.
* NSS TLS key log at `/data/data/<pkg>/files/_tls_keylog.log` when Conscrypt/JSSE/Cronet key logging surfaces are reachable. Use this with a pcap in Wireshark for apps that use custom CAs, certificate pinning, or QUIC without disabling their TLS checks.
* Chunked `CAPTURE_JSON` messages in logcat with the `NetTap` tag. Best-effort only — Android's per-uid logcat rate limiter silently drops chunks on large payloads, so don't try to reconstruct complete bodies from logcat. Records over `LOGCAT_MAX_JSON_CHARS` are skipped on this channel entirely; the JSONL file still receives them.

To tail the capture file live (LSPosed implies root):

```bash
adb shell 'su -c "tail -F /data/data/<pkg>/files/_okhttp_capture.jsonl"' | jq .
```

To tail TLS secrets live:

```bash
adb shell 'su -c "tail -F /data/data/<pkg>/files/_tls_keylog.log"'
```

## Hook installation order

Per host app, on `handleLoadPackage`:

1. **Shaded-OkHttp discovery** — `ShadedOkHttpDiscovery` runs a two-step ladder:
   1. Stock fast path — load canonical `okhttp3.*` FQNs.
   2. Pivot on `okhttp3.internal.http.RealInterceptorChain` (which survives heavy R8) to recover `Request` / `Response` / other OkHttp types by reflection.
2. **OkHttp hooks** (four-level fallback, first-one-wins):
   1. `okhttp3.internal.connection.RealCall.getResponseWithInterceptorChain$okhttp` (OkHttp 4)
   2. `okhttp3.RealCall.getResponseWithInterceptorChain` (OkHttp 3)
   3. `RealInterceptorChain.proceed(Request)` (survives rename — works even on fully obfuscated builds)
   4. `Exchange.writeRequestHeaders` (request-only telemetry)
3. **Cronet** — `CronetUrlRequestHook` + `CronetBidirectionalStreamHook` + `CronetUploadDataProviderHook`, each matching canonical `org.chromium.net.impl.*` classes.
4. **gRPC** — `GrpcCallInstaller` hooks `ClientCallImpl.start` / `sendMessage`; the listener is wrapped via `GrpcListenerProxy`.
5. **`HttpURLConnection`** — `HttpURLConnectionHook` tees the I/O streams.
6. **Higher-level open clients** — Volley, Fuel, Apache HttpClient 5, Ktor CIO, and AndroidAsync install independently when their classes are present.
7. **TLS key log** — Conscrypt/JSSE and canonical Cronet/BoringSSL key log hooks install independently. Cronet key log injection uses the same canonical Cronet resolver as the Cronet app-layer hooks.

Any layer that doesn't apply is logged and skipped; the others install independently.

## Build

```
./gradlew :app:assembleDebug
```

The Gradle daemon is configured for JDK 21 and the project uses Android SDK Platform 36.1 and Build Tools 36.0.0. The APK lands in `app/build/outputs/apk/debug/`.

### GitHub Actions release builds

To build an installable release APK in the fork, configure these repository secrets under **Settings → Secrets and variables → Actions**:

* `KEYSTORE`: the release keystore encoded as base64, for example `base64 -w 0 net-tap-release.jks` on Linux.
* `STORE_PASSWORD`: the keystore password.
* `KEY_PASSWORD`: the signing key password.
* `KEY_ALIAS` (optional): the signing key alias; defaults to `release`.
* `KEYSTORE_SHA256` (optional): the SHA-256 checksum of the original keystore file. When set, the workflow checks the decoded `KEYSTORE` against it without printing the checksum or keystore. On Linux, obtain the checksum with `sha256sum net-tap-release.jks` and use only the first 64 hexadecimal characters.

Use a keystore you control, keep a secure backup, and reuse the same key for later releases. Android requires the same signing certificate to update an installed app. For a new key, `keytool -genkeypair -keystore net-tap-release.jks -alias release -keyalg RSA -keysize 3072 -validity 10000` prompts for the passwords and certificate details. Do not commit the keystore or its passwords.

After the workflow is on the default branch, open **Actions → Android Package → Run workflow**. Choose the branch to build, leave `publish_release` unchecked, and download the `net-tap-release` artifact from the completed run. When signing secrets are configured, it contains `net-tap-release.apk`; without them it contains `net-tap-release-unsigned.apk`, which must be signed before installation. Pull requests from forks do not receive repository secrets and produce an unsigned release artifact.

Pushing a `v*` tag creates or updates the matching GitHub Release with the verified signed release and debug APKs. Alternatively, run the workflow manually on an existing `v*` tag and check `publish_release`. Publication fails if signing secrets are missing, incomplete, or the APK signature cannot be verified. The tag does not automatically change the app's `versionCode` or `versionName`; update those in `app/build.gradle` when preparing a version.

## Install

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

Enable the module in LSPosed and add the target app(s) to its scope. The module declares `xposed_scope = *`, so you choose the scope from LSPosed.

For a local signed release, set `NETTAP_SIGNING_STORE_FILE` to the absolute keystore path and set `NETTAP_SIGNING_STORE_PASSWORD`, `NETTAP_SIGNING_KEY_ALIAS`, and `NETTAP_SIGNING_KEY_PASSWORD`, then run `./gradlew :app:assembleRelease`. The existing `keystore.properties` configuration is also supported. With neither configuration, the release APK is unsigned.

## Test

```
./gradlew :app:testDebugUnitTest
```

Unit tests cover the pure-Java parts (body accumulators, header extraction, shaded-OkHttp discovery, metrics, tee streams, gRPC metadata parsing). Xposed-linked hook installers are covered only indirectly on the JVM since the Xposed API is `compileOnly`.

## Known limits

* **gRPC streaming** — only the first request message and first response message are captured; additional messages in client-streaming or server-streaming calls are ignored.
* **Proto payloads** — emitted as base64 blobs (`[grpc-proto base64:...]`) with no schema decoding.
* **Application-layer socket protocols** — anything that isn't HTTP/HTTP/2 (protobuf-over-TCP frameworks, XMPP, SIP, WebRTC signaling, etc.) is out of scope.
* **HTTP/2 SSLSocket fallback** — intentionally not implemented; the cost/benefit vs the existing layers doesn't justify the complexity of HPACK + h2 frame reassembly.
* **TLS bypass** — intentionally not implemented by default. If a target app pins certificates, prefer the app-layer hooks and `_tls_keylog.log`; generic SSL bypass changes target behavior and still will not decode custom application encryption.
