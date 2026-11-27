/* eslint-disable no-console */
/* Every en.json key must exist in each locale, and a value identical to the en.json one means
   the key was never translated.
   Exempt: values made only of protected terms (i18n-glossary.json) and the loanwords
   listed in i18n-loanwords.json ("all" applies to every locale). Consumed by i18n-checker.js. */
import fs from 'node:fs';

import { DEFAULT_LANG } from './constants/Lang.js';
import { stripGlossaryTerms } from './i18n-glossary.js';
import { readLangFile, readLangFiles } from './i18n-lang-files.js';

const loanwords = JSON.parse(fs.readFileSync('scripts/i18n-loanwords.json', 'utf8'));
const english = readLangFile(DEFAULT_LANG);

const PLACEHOLDER_RE = /\{\{[^{}]+\}\}|\{[^{}]+\}|<\/?[a-zA-Z0-9]+\s*\/?>/g;

// Only Latin letters can be left-over English: CJK / Cyrillic values are native names.
const hasTranslatableText = value => /[A-Za-z]/.test(stripGlossaryTerms(value.replace(PLACEHOLDER_RE, ' ')));

const isLoanword = (lang, value) => (loanwords.all ?? []).includes(value) || (loanwords[lang] ?? []).includes(value);

export const collectUntranslatedViolations = () => {
  const violations = [];

  for (const [lang, translated] of readLangFiles()) {
    if (lang === DEFAULT_LANG) continue;

    for (const [key, enValue] of Object.entries(english)) {
      if (typeof enValue !== 'string') continue;
      // Parity first: keys reaching t() through a variable (backend-provided labels) are invisible
      // to the literal t('…') scan, so a key absent from the locale would otherwise go unnoticed.
      if (!Object.hasOwn(translated, key)) {
        violations.push({
          lang,
          key,
          value: enValue,
          missing: true,
        });
        continue;
      }
      if (translated[key] !== enValue) continue;
      if (hasTranslatableText(enValue) && !isLoanword(lang, enValue)) {
        violations.push({
          lang,
          key,
          value: enValue,
        });
      }
    }
  }

  return violations;
};

export const reportUntranslatedViolations = (violations) => {
  for (const v of violations) {
    console.error(`${v.lang}.json — ${v.missing ? 'key missing from the file' : 'identical to the English value'}`);
    console.error(`  key   : ${v.key.slice(0, 90)}`);
    if (v.value !== v.key) console.error(`  value : ${v.value.slice(0, 90)}`);
  }
  console.error(`Total: ${violations.length} missing or untranslated value(s).`);
  console.error('Run "yarn auto-translation:all" to translate them. If a value is legitimately identical');
  console.error('(loanword, language name), add it to scripts/i18n-loanwords.json under the locale, e.g. "de": ["Dashboard"].');
};
