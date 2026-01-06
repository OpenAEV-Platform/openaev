import { exec } from 'node:child_process';
import { promises } from 'node:fs';
import { basename } from 'node:path';
import { promisify } from 'node:util';

import { DEFAULT_LANG } from './constants/Lang.js';

const execAsync = promisify(exec);

// deepl-free API key
const subscriptionKey = process.env.SUBSCRIPTION_KEY;

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
        const { stdout } = await execAsync(command);
        // eslint-disable-next-line no-console
        console.log(stdout);
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
