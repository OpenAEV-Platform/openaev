/* eslint-disable no-underscore-dangle */
import fs from 'node:fs';

import path from 'path';
import { fileURLToPath } from 'url';

import { DEFAULT_LANG, supportedLanguages } from './constants/Lang.js';

const __filename = fileURLToPath(import.meta.url);

const __dirname = `${path.dirname(__filename)}/../src`;

const escapeString = (inputString) => {
  return inputString.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replaceAll('"', '\\\\"');
};

// -- Retrieve i18n lang keys --

const computeLangKeys = (lang) => {
  const basePath = path.join(__dirname, 'utils', 'lang');

  // fallback to English if unsupported
  const targetLang = supportedLanguages.includes(lang) ? lang : DEFAULT_LANG;
  const filePath = path.join(basePath, `${targetLang}.json`);

  try {
    return fs.readFileSync(filePath, 'utf8');
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
      const regexp = /(?<![a-zA-Z])t\('([^']+)'\)/g;
      const matches = [...data.matchAll(regexp)];
      matches.forEach((m) => {
        const escapedMatch = escapeString(m[1]);
        const regexWithQuote = new RegExp(String.raw`"${escapedMatch}":`, 'g');
        const regexWithoutQuote = new RegExp(String.raw`${escapedMatch}:`, 'g');
        if (!langI18n.match(regexWithQuote) && !langI18n.match(regexWithoutQuote)) {
          results.push(m[1]);
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
    process.exit(1);
  } else {
    process.exit(0);
  }
};

run();
