# Full-Stack Course Project Portfolio

This directory contains four complete applications reconstructed from the course specifications. Each project is independently runnable, tested, documented, and designed to demonstrate a different production stack without requiring paid services for its default local workflow.

| Project | Product | Primary stack | Credential-free local path |
| --- | --- | --- | --- |
| [Agent AI](./agent-ai/README.md) | PDF-grounded question answering with optional web comparison | Node.js 22, Express 5, React 19, Vite, MCP | Extractive local retrieval and answers |
| [OnlineOrder](./onlineorder/README.md) | Secure restaurant browsing, cart, and checkout | Java 21, Spring Boot 3, PostgreSQL, React 18, Ant Design | H2-backed tests; PostgreSQL through Compose for the full app |
| [SocialAI](./socialai/README.md) | Authenticated media collection with AI image generation | Go, React 18, Vite, pluggable storage/search/AI adapters | JSON persistence, local media, generated SVG previews |
| [Spotify Local](./spotify/README.md) | Native album, favorites, and music-playback experience | Kotlin, Ktor, Android Compose, Hilt, Retrofit, Room, Media3 | Generated local covers and royalty-free WAV fixtures |

## Repository layout

```text
projects/
├── README.md                  Portfolio index and verification handoff
├── agent-ai/
│   ├── client/                React/Vite browser app
│   ├── server/                Express API, RAG, PDF, and MCP layers
│   ├── pnpm-lock.yaml         Shared workspace dependency lock
│   └── docker-compose.yml     Nginx web + API stack
├── onlineorder/
│   ├── backend/               Spring Boot/Gradle API
│   ├── frontend/              React/Vite/Ant Design client
│   └── docker-compose.yml     PostgreSQL + API + Nginx web stack
├── socialai/
│   ├── backend/               Go API and adapter implementations
│   ├── web/                   React/Vite client
│   ├── data/ and media/       Ignored local persistence locations
│   ├── .github/workflows/     Go and web CI
│   └── docker-compose.yml     API + Nginx web stack
└── spotify/
    ├── backend/               Independent Ktor/Gradle API
    ├── android/               Independent Android/Gradle application
    ├── scripts/               Fixture smoke and offline structure checks
    └── compose.yaml           Containerized Ktor API
```

There is intentionally no shared application runtime or root dependency graph. Each project owns its lockfile, Gradle wrapper, environment template, tests, and deployment files. That keeps builds reproducible and lets a reviewer evaluate one project without installing the other stacks.

This repository is organized as a portfolio monorepo. The root README is the repository landing page, while each application keeps its own dependency graph, lockfiles, environment template, tests, deployment files, and subtree-specific ignore rules.

Several services default to port `8080`—OnlineOrder, SocialAI, and Spotify's backend—so run them one at a time or change their documented port settings. Agent AI uses API port `5001` by default.

## Project highlights

### Agent AI

Agent AI is a local-first retrieval-augmented generation application. A user uploads a real PDF, receives an opaque expiring document session, asks questions, and sees page-aware source excerpts with each grounded answer. The responsive React interface includes drag-and-drop upload, conversation history, dictation, speech playback, keyboard operation, and user-facing error states.

The Express API performs PDF validation and extraction, deterministic passage ranking, bounded uploads and questions, rate limiting, CORS allowlisting, and in-memory session expiry. Local mode needs no credentials. Optional provider boundaries add OpenAI Responses synthesis and an MCP stdio child server backed by SerpAPI without exposing keys to the browser.

Portfolio signals:

- real document ingestion and source provenance rather than canned chat responses;
- a testable local fallback around optional AI and search providers;
- per-document isolation instead of a process-global upload path;
- security headers, origin controls, limits, expiry, and early deletion;
- containerized Nginx frontend and private API routing.

### OnlineOrder / Lai Food

OnlineOrder is a transactional full-stack food-ordering application. Customers can register, sign in, browse seeded restaurants and menus, add quantities to a personal cart, see precise decimal totals, and check out. PostgreSQL schema constraints and indexes enforce the domain model, while idempotent seed scripts make repeated local starts safe.

The Java 21 backend uses Spring Boot Web, Security, Data JDBC, Validation, Actuator, Caffeine, BCrypt, server-side sessions, and CSRF protection. The React 18/Ant Design client handles authentication, menu browsing, cart interactions, loading, and failures. Docker Compose connects PostgreSQL, a non-root API image, and an Nginx frontend.

Portfolio signals:

- session fixation protection and CSRF on state-changing browser requests;
- passwords in request bodies and BCrypt hashes at rest;
- authenticated, per-customer carts with mutation-driven cache eviction;
- H2-backed service and HTTP integration tests independent of Docker;
- production JAR and frontend bundle verification.

### SocialAI

SocialAI is an authenticated media-sharing application. Users can register, sign in, upload images or videos, search by creator and all caption terms, delete only their own posts, generate an AI image preview, discard it, or publish it into the collection.

The Go backend deliberately uses the standard library and interfaces around three replaceable boundaries: repository (`memory`, JSON file, or Elasticsearch), media (`local` or Google Cloud Storage), and image generation (local SVG or OpenAI). The React app provides register, login, creation, generation, publishing, and collection flows. Local defaults need no cloud account.

Portfolio signals:

- PBKDF2 password hashing and strictly validated, expiring HS256 tokens;
- upload size/type validation, storage traversal protection, CORS, and ownership checks;
- race-tested repository and HTTP behavior;
- optional Elasticsearch, GCS, and OpenAI adapters behind local implementations;
- a verified end-to-end browser journey from registration through AI publishing.

### Spotify Local

Spotify Local pairs a Ktor fixture API with a native Android application. The app presents feed sections, navigates to playlist details, persists favorite albums in Room, and controls Media3/ExoPlayer through an activity-scoped floating player with play, pause, progress, and seek behavior.

The Android client uses Compose, MVVM, `StateFlow`, Hilt, Retrofit, Navigation Compose, Room, Coil, and a playback interface that can be replaced in unit tests. The Ktor server preserves the documented feed/playlist/song contracts while generating deterministic SVG covers and five-second WAV tracks at request time, avoiding copyrighted binaries and external media hosting.

Portfolio signals:

- API, repository, database, ViewModel, navigation, and playback separation;
- local favorites that survive process restarts;
- shared playback UI across Home, Favorites, and Playlist destinations;
- complete debug APK, Android-test APK, Hilt/Room code generation, and lint verification;
- credential-free, referentially validated sample media.

## Verification snapshot

The following commands and results were verified on 2026-07-11. Test counts refer to executed test cases/functions, not just test files.

### Agent AI — 21 tests

```bash
cd agent-ai
pnpm test
pnpm check
pnpm build
pnpm audit --audit-level high
```

- Server: 18 passing Node tests.
- Client: 3 passing Vitest interaction tests.
- Syntax/type-adjacent checks, production client build, and high-severity dependency audit passed.
- The localhost UI was rendered in a browser with no console errors.

Environment-only limits: Docker was unavailable, so Compose images were not started. The credential-free PDF/RAG path was verified; live OpenAI and SerpAPI calls require user-owned keys and were not exercised.

### OnlineOrder — 24 tests

```bash
cd onlineorder/backend
./gradlew clean test bootJar

cd ../frontend
pnpm install --frozen-lockfile
pnpm audit --audit-level=high
pnpm test
pnpm build
```

- Backend: 15 passing tests—7 authenticated API integration cases and 8 service cases.
- Frontend: 9 passing Vitest cases.
- Spring Boot JAR, production web bundle, and high-severity dependency audit passed.

Environment-only limits: Docker was unavailable, so the PostgreSQL Compose stack was not launched. The backend integration suite used its PostgreSQL-compatible H2 test profile; a real PostgreSQL smoke run remains an environment step.

### SocialAI — 26 tests

```bash
cd socialai/backend
go test -race ./... -count=1
go vet ./...
go build -o /tmp/socialai-server ./cmd/server

cd ../web
pnpm test
pnpm build
pnpm audit --audit-level high
```

- Backend: 20 passing Go test functions under the race detector.
- Frontend: 2 passing files / 6 passing Vitest cases.
- `go vet`, server binary build, production web bundle, and high-severity dependency audit passed.
- A live local API and browser flow completed register → login → local image generation → publish → collection with no console errors.

Environment-only limits: Docker was unavailable. Local adapters were exercised; Elasticsearch, GCS, and OpenAI need external services or credentials and were not called live.

### Spotify Local — 13 executed tests, plus one packaged instrumentation test

```bash
cd spotify/backend
./gradlew test
PUBLIC_BASE_URL=http://127.0.0.1:8080 ./gradlew run

# In a second terminal while the backend is running:
cd ..
BASE_URL=http://127.0.0.1:8080 ./scripts/check-fixtures.sh
python3 scripts/validate-project.py

cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
./gradlew assembleDebugAndroidTest
```

- Backend: 7 passing Ktor route, error, serialization, audio, byte-range, cover, health, and fixture tests.
- Android JVM: 6 passing Retrofit-contract, Home ViewModel, and Player ViewModel tests.
- Live Ktor smoke check passed `/health`, `/feed`, `/playlists`, `/playlist/1`, RIFF audio validation, and a partial-content byte-range request.
- Debug APK assembled and its v2 signature verified with `apksigner`.
- Android lint completed with 0 errors; its 15 warnings were newer-dependency notices rather than code defects.
- The Room DAO instrumentation test compiled and packaged into the Android-test APK.

Environment-only limits: no emulator or physical Android device was available, so `connectedDebugAndroidTest` and hands-on Media3 playback were not executed. Docker was also unavailable. The backend audio itself was served and validated from a real process.

### Portfolio total

The verified suites executed **84 tests**: 21 Agent AI, 24 OnlineOrder, 26 SocialAI, and 13 Spotify backend/JVM tests. Browser smoke checks, static checks, builds, lint, audits, race detection, fixture validation, APK verification, and the compiled Room instrumentation test are additional evidence and are not included in that number.

## Repository hygiene audit

The source-level audit found no credentials or private keys in the project trees:

- There are no real `.env`, keystore, PEM, service-account, or local Android property files under the four project trees.
- Every `.env.example` contains blank credential fields or safe localhost values only.
- Agent AI's upload directory, and SocialAI's data/media directories, contain only `.gitkeep`; no user uploads or persisted records were found.
- OpenAI, SerpAPI, PostgreSQL, JWT, Elasticsearch, and cloud credential values remain external configuration.
- Gradle Wrapper JARs, pnpm lockfiles, SocialAI's CI workflow, and Spotify's exported Room schema are intentional source artifacts.
- Shared Java and Go verification toolchains live outside `projects/`; the temporary Android SDK was removed. None are part of this portfolio tree.

Generated dependencies, build products, Gradle caches, temporary SDKs, and verification binaries were removed before packaging:

| Project | Present generated material | Publication status |
| --- | --- | --- |
| Agent AI | None | Ignore rules cover future `node_modules` and `dist` output |
| OnlineOrder | None | Ignore rules cover future frontend and Gradle output |
| SocialAI | None | Local `data`/`media` payloads and web output remain ignored |
| Spotify | None | Verification SDK, build, Gradle, and Kotlin cache directories were removed; ignore rules cover future regeneration |

The source portfolio is published as one monorepo so reviewers can discover all four applications from this landing page while still building and running each project independently.

## Running the portfolio

Start with the individual project README linked in the table above. Each contains its exact prerequisites, configuration, API contract, direct-development commands, Docker option, security notes, and troubleshooting guidance.

For a clean review workflow:

1. Choose one project and copy its `.env.example` to `.env` only when its local instructions require it.
2. Install dependencies from its checked-in lockfile or use its Gradle wrapper.
3. Run that project's verified test commands before starting services.
4. Use its local, credential-free adapters first.
5. Enable paid/cloud adapters only with scoped credentials stored outside source control.

The applications are deliberately separate portfolio pieces rather than microservices that depend on one another.
