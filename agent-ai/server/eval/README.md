# Reproducible retrieval smoke evaluation

`fixtures/harbor-guide.pdf` is a three-page **fictional**, CC0-1.0 guide authored for this repository. `guide.json` is its complete editable text; `questions.json` records eight answerable questions with independently specified page labels and two absent-topic questions. It includes no personal documents or service advice. Rebuild with `node eval/build-fixture.js` from `server/`.

Run `pnpm eval` from `agent-ai/`. The evaluator parses the actual checked-in PDF, ranks chunks using the production ranker, and reports page recall at 1 and 4 plus absent-topic no-match accuracy for two chunk configurations. CI gates the default configuration at recall@4 >= 0.75 and both absent topics rejected, preserving useful slack instead of requiring a printed score. The comparison configuration is descriptive only.

This small, authored dataset catches ingestion/provenance/retrieval regressions; it is not held-out evidence of general QA quality, semantic retrieval, answer faithfulness, or superiority over another method. Labels were written with the guide and should not be tuned just to pass. Add independently sourced, permitted documents and paraphrases before making general performance claims.
