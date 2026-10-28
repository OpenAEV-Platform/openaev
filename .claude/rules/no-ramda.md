---
paths:
  - "openaev-front/**/*.{ts,tsx,js,jsx}"
  - "openaev-front/package.json"
---

<!-- The rule itself lives in .github/instructions/frontend.instructions.md (Components section). -->

Do not use Ramda: write native JavaScript (array methods, spread, optional chaining). Ramda is being
removed, so never add a `ramda` import, and never add a file to `legacyRamdaFiles` in
`openaev-front/eslint.config.js`. When you touch a file from that list, prefer removing its Ramda calls
and dropping it from the list. See `.github/instructions/frontend.instructions.md`.
