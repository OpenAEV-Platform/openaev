/* The translation files of src/utils/lang, read by the i18n checks. */
import fs from 'node:fs';

import { supportedLanguages } from './constants/Lang.js';

const langFile = lang => `src/utils/lang/${lang}.json`;

export const readLangFile = lang => JSON.parse(fs.readFileSync(langFile(lang), 'utf8'));

// Each supported language that has a file, with its content: [[lang, json], ...].
export const readLangFiles = () => supportedLanguages
  .filter(lang => fs.existsSync(langFile(lang)))
  .map(lang => [lang, readLangFile(lang)]);
