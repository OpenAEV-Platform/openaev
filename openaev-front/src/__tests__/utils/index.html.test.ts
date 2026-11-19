import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

// The backend (HomeApi) swaps %CSP_NONCE% for a per-response nonce and sends a CSP that only
// allows inline scripts carrying it. An inline script added to index.html without the placeholder
// would be reported (then blocked) once the policy is enforced.
const indexHtml = readFileSync(resolve(__dirname, '../../../index.html'), 'utf-8');
const inlineScripts = [...indexHtml.matchAll(/<script(?![^>]*\ssrc=)([^>]*)>/g)];

describe('index.html content security policy contract', () => {
  it('has an inline script, the one setting the base path', () => {
    expect(inlineScripts).toHaveLength(1);
    expect(indexHtml).toContain('window.BASE_PATH');
  });

  it('gives every inline script the nonce placeholder', () => {
    for (const [, attributes] of inlineScripts) {
      expect(attributes).toContain('nonce="%CSP_NONCE%"');
    }
  });

  it('has no inline event handler, which a nonce cannot authorize', () => {
    expect(indexHtml).not.toMatch(/\son[a-z]+\s*=/i);
  });
});
