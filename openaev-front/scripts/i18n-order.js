/* eslint-disable no-console */
/* Every lang file keeps its keys in the order written by sort-translation-files.js, so that adding keys never moves
   others. Consumed by i18n-checker.js. */
import { readLangFiles } from './i18n-lang-files.js';

export const compareKeys = (a, b) => a.toLowerCase().localeCompare(b.toLowerCase(), 'en');

export const collectOrderViolations = () => {
  const violations = [];

  for (const [lang, translations] of readLangFiles()) {
    const keys = Object.keys(translations);
    const sorted = [...keys].sort(compareKeys);
    const index = keys.findIndex((key, i) => key !== sorted[i]);
    if (index !== -1) {
      violations.push({
        lang,
        key: keys[index],
        expected: sorted[index],
      });
    }
  }

  return violations;
};

export const reportOrderViolations = (violations) => {
  for (const v of violations) {
    console.error(`${v.lang}.json — keys out of order`);
    console.error(`  found    : ${v.key.slice(0, 90)}`);
    console.error(`  expected : ${v.expected.slice(0, 90)}`);
  }
  console.error(`Total: ${violations.length} unsorted file(s). Run: node scripts/sort-translation-files.js`);
};
