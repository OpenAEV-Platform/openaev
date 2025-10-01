/* eslint-disable no-underscore-dangle */
import fs from 'node:fs';

import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);

const __dirname = `${path.dirname(__filename)}/../src`;

const escapeString = (inputString) => {
  return inputString.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replaceAll('"', '\\\\"');
};

// -- Retrieve i18n lang keys --

const computeLangKeys = (lang) => {
  let data;
  if (lang === 'en') {
    data = fs.readFileSync(`${__dirname}/utils/lang/en.json`, { encoding: 'utf8' });
  } else if (lang === 'fr') {
    data = fs.readFileSync(`${__dirname}/utils/lang/fr.json`, { encoding: 'utf8' });
  } else if (lang === 'zh') {
    data = fs.readFileSync(`${__dirname}/utils/lang/zh.json`, { encoding: 'utf8' });
  }
  return data;
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
  const languages = ['en', 'fr', 'zh'];
  const missingKeys = {};

  languages.forEach((lang) => {
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
