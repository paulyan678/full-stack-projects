# Engineering decisions and evidence

## Agent AI: simple retrieval, explicit request ownership

Lexical ranking makes the credential-free path deterministic and inspectable: 900-character page chunks overlap by 120 characters, with top-four TF-IDF-style ranking. It misses semantic paraphrases and is not a trained retrieval model. The checked-in CC0 fixture compares a smaller chunk configuration without claiming benchmark superiority. Optional provider boundaries have local success/error contracts; live OpenAI/SerpAPI use remains a separate integration step.

Each upload increments a browser generation and aborts earlier work. A generation check is still required because cancellation is best effort and responses can already be in flight. Obsolete uploaded sessions are deleted if their IDs arrive late. Server expiry rejects access immediately at TTL; a periodic sweep removes unused content within one further minute of responsive event-loop time. Shutdown clears the process-local store. There is no authenticated account ownership or multi-instance persistence.

## OnlineOrder: correctness before cart caching

Cart snapshots are read directly from the database at repeatable-read isolation. Row locking serializes mutations, but post-mutation cache eviction alone cannot prevent an overlapping old read from repopulating a cache. A consistent read snapshot also prevents a checkout between total and line-item queries from producing a mixed response. Restaurant/menu data can remain cached. Deterministic stale-read and real PostgreSQL interleaving tests exercise these decisions. See the application README for how to run the disposable database suite.

## Spotify: verify the device boundary

The Ktor API uses small, deterministic WAV fixtures and manual bounded byte ranges to make seeking testable without licensed tracks or commercial APIs. Android keeps a process-scoped player behind a controller interface. Unit tests verify delegation; device instrumentation separately executes SVG decoding, Room disk reopening, and real Media3 playback/seeking. Those tests complement rather than replace a manual full application run. The source-driven README animation is a rendering from fixtures, not an Android recording. Background playback services, audio-focus behavior, and remote catalog ingestion remain outside the current scope.

## SocialAI: replaceable adapters are not distributed transactions

Local implementations make the main workflow reproducible without credentials. Cloud HTTP contract tests check serialization, status handling, auth headers with synthetic tokens, and ownership decisions, but cannot establish provider IAM or deployed-service compatibility. Media and metadata operations are not atomic across adapters. Failed preview publication/discard is retryable; failed cleanup can leave orphan objects, and process-local preview state does not survive restart. Production retention requires durable bookkeeping and reconciliation.

## Dependencies, CI, and attribution

Root workflows use the application subdirectories explicitly and publish test reports. Agent AI dependencies were updated within their existing major versions; SocialAI's test runner moved from Vitest 3 to 4 to eliminate the vulnerable Tinypool dependency rather than forcing an incompatible transitive major override. SocialAI also moves React Router to 7.18 for the patched redirect/hydration behavior; the existing declarative router API is retained and its authentication/navigation tests are rerun on React 18. Dependency audit results describe a particular lockfile and registry snapshot, not a guarantee against every exploit. The CI audit gate is retained to expose later advisories.

Existing Git history, names, namespaces (including `com.laioffer`), and notices are preserved. The contributions in this change are documented by focused commits and behavioral regressions: cart consistency, request cancellation/retention, decoder registration, adapter contracts, reproducible evaluation, and root-level CI. No new upstream attribution is asserted without a verifiable source.
