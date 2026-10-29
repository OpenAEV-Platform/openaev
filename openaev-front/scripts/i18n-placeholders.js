/* eslint-disable no-console */
/* A translation must keep the same placeholders ({x}, {{x}}, ${x}) and tags (<a href='…'>, </a>) as the en.json value,
   and must not contain a literal "undefined" (a known artefact of the auto-translation tool).
   Consumed by i18n-checker.js. */
import fs from 'node:fs';

import { DEFAULT_LANG, supportedLanguages } from './constants/Lang.js';
import tokens from './i18n-tokens.js';

const english = JSON.parse(fs.readFileSync(`src/utils/lang/${DEFAULT_LANG}.json`, 'utf8'));

const UNDEFINED_RE = /\bundefined\b/;

export const collectPlaceholderViolations = () => {
  const violations = [];

  for (const lang of supportedLanguages) {
    if (lang === DEFAULT_LANG) continue;
    const file = `src/utils/lang/${lang}.json`;
    if (!fs.existsSync(file)) continue;

    for (const [key, value] of Object.entries(JSON.parse(fs.readFileSync(file, 'utf8')))) {
      const enValue = english[key];
      if (typeof enValue !== 'string' || typeof value !== 'string') continue;

      const expected = tokens(enValue);
      const actual = tokens(value);
      if (UNDEFINED_RE.test(value) && !UNDEFINED_RE.test(enValue)) {
        violations.push({
          lang,
          key,
          value,
          reason: 'contains "undefined"',
        });
      } else if (expected.join('\0') !== actual.join('\0')) {
        violations.push({
          lang,
          key,
          value,
          reason: `placeholders differ, expected [${expected.join(' ')}] got [${actual.join(' ')}]`,
        });
      }
    }
  }

  return violations;
};

export const reportPlaceholderViolations = (violations) => {
  for (const v of violations) {
    console.error(`${v.lang}.json — ${v.reason}`);
    console.error(`  key   : ${v.key.slice(0, 90)}`);
    console.error(`  value : ${v.value.slice(0, 90)}`);
  }
  console.error(`Total: ${violations.length} placeholder violation(s).`);
};
