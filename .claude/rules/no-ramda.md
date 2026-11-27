---
paths:
  - "openaev-front/**/*.{ts,tsx,js,jsx}"
  - "openaev-front/package.json"
---

<!-- The rule itself lives in .github/instructions/frontend.instructions.md (Components section). -->

Do not use Ramda: write native JavaScript (array methods, spread, optional chaining). Ramda has been
removed: never add the `ramda` dependency or a `ramda` import back. See
`.github/instructions/frontend.instructions.md`.
