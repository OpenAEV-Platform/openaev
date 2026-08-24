/* eslint-disable no-console */
/* Terms that must never be translated (acronyms, standards, product names).
   Consumed by i18n-checker.js. */
import fs from 'node:fs';

import { supportedLanguages } from './constants/Lang.js';

const { globalTerms } = JSON.parse(fs.readFileSync('scripts/i18n-glossary.json', 'utf8'));

// Escaping: a term such as "C++" would make the RegExp throw without it.
const escapeRe = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// Word boundaries so "URL" is not matched inside "CURL"; the `s?` allows the key's English plural.
const wordRe = term => new RegExp(`\\b${escapeRe(term)}s?\\b`, 'i');

export const collectGlossaryViolations = () => {
  const violations = [];

  for (const lang of supportedLanguages) {
    const file = `src/utils/lang/${lang}.json`;
    if (!fs.existsSync(file)) continue;

    for (const [key, value] of Object.entries(JSON.parse(fs.readFileSync(file, 'utf8')))) {
      if (value === key) continue; // not translated at all: different defect, different check
      // Technical keys (openaev_caldera…)
      if (/^[a-z0-9]+([_-][a-z0-9]+)+$/.test(key)) continue;

      for (const term of globalTerms) {
        if (wordRe(term).test(key) && !value.toLowerCase().includes(term.toLowerCase())) {
          violations.push({
            lang,
            term,
            key,
            value,
          });
        }
      }
    }
  }

  return violations;
};

export const reportGlossaryViolations = (violations) => {
  for (const v of violations) {
    console.error(`${v.lang}.json — "${v.term}" missing from the translation`);
    console.error(`  key   : ${v.key.slice(0, 90)}`);
    console.error(`  value : ${v.value.slice(0, 90)}`);
  }
  console.error(`Total: ${violations.length} glossary violation(s).`);
};
