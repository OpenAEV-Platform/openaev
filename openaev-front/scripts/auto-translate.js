import { exec } from 'node:child_process';
import { promises } from 'node:fs';
import { basename } from 'node:path';
import { promisify } from 'node:util';

import { DEFAULT_LANG } from './constants/Lang.js';
import matchSourceCasing from './i18n-casing.js';

const execAsync = promisify(exec);

// deepl-free API key
const subscriptionKey = process.env.SUBSCRIPTION_KEY;

const langFile = code => `src/utils/lang/${code}.json`;
const readLang = async code => JSON.parse(await promises.readFile(langFile(code), 'utf8'));

// DeepL capitalises a lowercase source ("breached"): only the keys the tool just added are adjusted.
async function matchCasingOfNewKeys(code, before) {
  const [english, translated] = await Promise.all([readLang(DEFAULT_LANG), readLang(code)]);
  let adjusted = 0;
  for (const [key, value] of Object.entries(translated)) {
    if (key in before || typeof value !== 'string' || typeof english[key] !== 'string') continue;
    const matched = matchSourceCasing(english[key], value);
    if (matched !== value) {
      translated[key] = matched;
      adjusted += 1;
    }
  }
  if (adjusted) await promises.writeFile(langFile(code), JSON.stringify(translated, null, 2));
  // eslint-disable-next-line no-console
  console.log(`${code}: ${adjusted} value(s) adjusted to the casing of the source`);
}

async function translateFiles() {
  // eslint-disable-next-line no-console
  console.log('Translation process started...');
  if (!subscriptionKey) {
    throw new Error('SUBSCRIPTION_KEY environment variable is not set. Aborting.');
  }

  try {
    // extract the available languages from the translation files name
    const langDir = 'src/utils/lang';
    const files = await promises.readdir(langDir);
    const languageCodes = files
      .filter(file => file.endsWith('.json'))
      .map(file => basename(file, '.json'))
      .filter(code => code.length === 2 && code !== DEFAULT_LANG); // Exclude 'en' since it's the default langue and source
    // eslint-disable-next-line no-console
    console.log(`Translating from English to [${languageCodes}]`);

    for (const code of languageCodes) {
      const command = `i18n-auto-translation -a deepl-free -p src/utils/lang/en.json -t ${code} -k ${subscriptionKey}`;
      try {
        // eslint-disable-next-line no-await-in-loop
        const before = await readLang(code).catch(() => ({})); // the file does not exist yet for a new locale
        // eslint-disable-next-line no-await-in-loop
        const { stdout } = await execAsync(command);
        // eslint-disable-next-line no-console
        console.log(stdout);
        // eslint-disable-next-line no-await-in-loop
        await matchCasingOfNewKeys(code, before);
      } catch (error) {
        // eslint-disable-next-line no-console
        console.error(`Error translating src/utils/lang/${code}.json:`, error.message);
      }
    }
    // eslint-disable-next-line no-console
    console.log('Translation process completed!');
  } catch (error) {
    // eslint-disable-next-line no-console
    console.error('Fatal error:', error.message);
    process.exit(1);
  }
}

// Run the script
await translateFiles();
