# Implementation playbook — moved to the design-system repository

**This document now lives in `XTM-Foundation/filigran-design-system`:**

**➜ [`process/PRODUCT-IMPLEMENTATION-PLAYBOOK.md`](https://github.com/XTM-Foundation/filigran-design-system/blob/main/process/PRODUCT-IMPLEMENTATION-PLAYBOOK.md)**

## Why it moved

It was written here, by this repository's pilot. The second product pilot then
found no reference to it anywhere in its own repository and had to be told where
it was — a document you have to announce is not findable. The library is the one
repository every pilot already has to look at, so the playbook moved next to the
library it describes.

## What moved with it

| Was here | Now, in `filigran-design-system` |
| --- | --- |
| `fds-migration/IMPLEMENTATION-PLAYBOOK.md` | `process/PRODUCT-IMPLEMENTATION-PLAYBOOK.md` |
| `fds-migration/PLAYBOOK-DEFECTS.md` | `process/PRODUCT-IMPLEMENTATION-PLAYBOOK-DEFECTS.md` |
| `fds-migration/artifacts/ci-design-system-secret.test.ts` | `process/artifacts/ci-design-system-secret.test.ts` |

The index of previous pilots is the table in the playbook's step 0.5, so it
travelled inside the document. **A new pilot adds its row there**, in a pull
request to the design-system repository, opened alongside the one that ships it.

## What stayed here

[`LIBRARY-FEEDBACK.md`](./LIBRARY-FEEDBACK.md) — it records what this product
found missing in the library while integrating it. It is this product's
observation and it belongs to this product.

The rest of `fds-migration/` is unchanged: it is this repository's own migration
state, mapping and log, not the general playbook.
