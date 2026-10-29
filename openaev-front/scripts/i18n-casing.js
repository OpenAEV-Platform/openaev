/* The case of the first letter of a translation follows the English source: a lowercase source
   ("breached", "output(s)") is a fragment meant to be inserted in a sentence, so it stays lowercase
   in every language, German included. DeepL capitalises it, hence this fix-up after the tool runs.
   Consumed by auto-translate.js. */
import fs from 'node:fs';

const { globalTerms } = JSON.parse(fs.readFileSync('scripts/i18n-glossary.json', 'utf8'));

// Placeholders, tags, digits and punctuation in front of the first letter do not count.
const SKIPPED_RE = /\$?\{\{[^{}]+\}\}|\$?\{[^{}]+\}|<\/?[a-zA-Z0-9]+\s*\/?>/y;

const firstLetterIndex = (value) => {
  let i = 0;
  while (i < value.length) {
    SKIPPED_RE.lastIndex = i;
    const skipped = SKIPPED_RE.exec(value);
    if (skipped) {
      i += skipped[0].length;
    } else if (/\p{L}/u.test(value[i])) {
      return i;
    } else {
      i += 1;
    }
  }
  return -1;
};

const isLower = char => char === char.toLowerCase() && char !== char.toUpperCase();
const isUpper = char => char === char.toUpperCase() && char !== char.toLowerCase();

// Brands, acronyms and CamelCase keep their case: "Kubernetes", "IPv4", "OpenCTI".
const keepsItsCase = (word) => {
  return /\p{Lu}/u.test(word.slice(1)) || globalTerms.some(term => term.toLowerCase() === word.toLowerCase());
};

const matchSourceCasing = (source, translation) => {
  const sourceIndex = firstLetterIndex(source);
  const translationIndex = firstLetterIndex(translation);
  if (sourceIndex < 0 || translationIndex < 0) return translation;

  const word = (translation.slice(translationIndex).match(/^[\p{L}\p{N}&+]+/u) ?? [''])[0];
  if (keepsItsCase(word)) return translation;
  // A word copied as is from the source is an identifier ("latest", "asset_ids_to_process", "Missing"): never touched.
  const copied = (translation.slice(translationIndex).match(/^[\p{L}\p{N}_-]+/u) ?? [''])[0];
  if (source.split(/[^\p{L}\p{N}_-]+/u).includes(copied)) return translation;

  const sourceChar = source[sourceIndex];
  const translationChar = translation[translationIndex];
  let fixed = translationChar;
  if (isLower(sourceChar) && isUpper(translationChar)) fixed = translationChar.toLowerCase();
  if (isUpper(sourceChar) && isLower(translationChar)) fixed = translationChar.toUpperCase();

  return translation.slice(0, translationIndex) + fixed + translation.slice(translationIndex + 1);
};

export default matchSourceCasing;
