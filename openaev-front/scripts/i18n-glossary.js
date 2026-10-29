/* eslint-disable no-console */
/* Terms that must never be translated (acronyms, standards, product names).
   Consumed by i18n-checker.js. */
import fs from 'node:fs';

import { DEFAULT_LANG, supportedLanguages } from './constants/Lang.js';

const { globalTerms } = JSON.parse(fs.readFileSync('scripts/i18n-glossary.json', 'utf8'));
const english = JSON.parse(fs.readFileSync(`src/utils/lang/${DEFAULT_LANG}.json`, 'utf8'));

// Escaping: a term such as "C++" would make the RegExp throw without it.
const escapeRe = s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// A space in a term also matches a hyphen: "attack path" covers "attack-path graph".
const termRe = s => escapeRe(s).replace(/ /g, '[ -]');
// Word boundaries so "URL" is not matched inside "CURL"; the `s?` allows the English plural.
const wordRe = term => new RegExp(`\\b${termRe(term)}s?\\b`, 'i');
// In the translation the term must also be a whole word: as a substring, "mac" would be found in "machine".
// Lookarounds rather than \b so that "IPアドレス" (CJK next to the term) still matches.
const inValueRe = term => new RegExp(`(?<![A-Za-z0-9])${termRe(term)}s?(?![A-Za-z0-9])`, 'i');

const terms = globalTerms.map(term => ({
  term,
  inReference: wordRe(term),
  inValue: inValueRe(term),
}));

// Longest term first, so "XTM Hub" is removed whole instead of leaving "Hub" behind.
const allTermsRe = new RegExp(
  `\\b(?:${[...globalTerms].sort((a, b) => b.length - a.length).map(escapeRe).join('|')})s?\\b`,
  'gi',
);

// What is left of a value once every protected term is removed (see i18n-untranslated.js).
export const stripGlossaryTerms = value => value.replace(allTermsRe, ' ');

export const collectGlossaryViolations = () => {
  const violations = [];

  for (const lang of supportedLanguages) {
    const file = `src/utils/lang/${lang}.json`;
    if (!fs.existsSync(file)) continue;

    for (const [key, value] of Object.entries(JSON.parse(fs.readFileSync(file, 'utf8')))) {
      // The reference is the English value, not the key: technical keys (openaev_caldera…) only carry it there.
      const reference = english[key];
      if (typeof reference !== 'string' || typeof value !== 'string') continue;
      if (value === reference) continue; // not translated at all: different defect, different check

      for (const { term, inReference, inValue } of terms) {
        if (inReference.test(reference) && !inValue.test(value)) {
          violations.push({
            lang,
            term,
            key,
            reference,
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
    if (v.reference !== v.key) console.error(`  en    : ${v.reference.slice(0, 90)}`);
    console.error(`  value : ${v.value.slice(0, 90)}`);
  }
  console.error(`Total: ${violations.length} glossary violation(s).`);
};
