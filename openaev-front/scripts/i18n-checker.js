/* eslint-disable no-underscore-dangle */
import fs from 'node:fs';

import path from 'path';
import { fileURLToPath } from 'url';

import { DEFAULT_LANG, supportedLanguages } from './constants/Lang.js';
import { collectGlossaryViolations, reportGlossaryViolations } from './i18n-glossary.js';
import { collectPlaceholderViolations, reportPlaceholderViolations } from './i18n-placeholders.js';
import { collectUntranslatedViolations, reportUntranslatedViolations } from './i18n-untranslated.js';

const __filename = fileURLToPath(import.meta.url);

const __dirname = `${path.dirname(__filename)}/../src`;

// -- Retrieve i18n lang keys --

const computeLangKeys = (lang) => {
  const basePath = path.join(__dirname, 'utils', 'lang');

  // fallback to English if unsupported
  const targetLang = supportedLanguages.includes(lang) ? lang : DEFAULT_LANG;
  const filePath = path.join(basePath, `${targetLang}.json`);

  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error(`Failed to read language file for "${targetLang}":`, err);
    return null;
  }
};

// -- Match missing keys --

const checkLanguageSupport = (lang) => {
  const results = [];
  const langI18n = computeLangKeys(lang);

  const match = (filePath) => {
    try {
      const data = fs.readFileSync(filePath, { encoding: 'utf8' });
      // Also catches t('… {count} …', { count }), double quotes and escaped quotes ('It\'s').
      const regexp = /(?<![\w.$])t\(\s*(?:'((?:[^'\\\n]|\\.)*)'|"((?:[^"\\\n]|\\.)*)")\s*[,)]/g;
      const matches = [...data.matchAll(regexp)];
      matches.forEach((m) => {
        const key = (m[1] ?? m[2]).replace(/\\(.)/g, '$1');
        if (!Object.hasOwn(langI18n, key)) {
          results.push(key);
        }
      });
    } catch (error) {
      return `Error reading file ${filePath}:${error}`;
    }
    return null;
  };

  const read = (dirPath) => {
    const files = fs.readdirSync(dirPath);
    files.forEach((file) => {
      const filePath = path.join(dirPath, file);
      const isDir = fs.lstatSync(filePath).isDirectory();
      if (!isDir) {
        match(filePath);
      } else {
        read(filePath);
      }
    });
  };
  read(__dirname);
  return results;
};

const run = () => {
  const missingKeys = {};

  supportedLanguages.forEach((lang) => {
    const keys = checkLanguageSupport(lang);
    if (keys.length > 0) {
      missingKeys[lang] = keys;
    }
  });

  if (Object.keys(missingKeys).length) {
    // eslint-disable-next-line no-console
    console.error('Missing keys :', missingKeys);
  }
  const glossaryViolations = collectGlossaryViolations();
  if (glossaryViolations.length) {
    reportGlossaryViolations(glossaryViolations);
  }

  const untranslatedViolations = collectUntranslatedViolations();
  if (untranslatedViolations.length) {
    reportUntranslatedViolations(untranslatedViolations);
  }
  const placeholderViolations = collectPlaceholderViolations();
  if (placeholderViolations.length) {
    reportPlaceholderViolations(placeholderViolations);
  }

  if (Object.keys(missingKeys).length
    || glossaryViolations.length
    || untranslatedViolations.length
    || placeholderViolations.length) {
    process.exit(1);
  }
  process.exit(0);
};

run();
