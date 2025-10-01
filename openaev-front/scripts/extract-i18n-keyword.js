import fs from 'node:fs';
import path from 'node:path';
import util from 'node:util';

const readdir = util.promisify(fs.readdir);
const stat = util.promisify(fs.stat);
const readFile = util.promisify(fs.readFile);
const writeFile = util.promisify(fs.writeFile);

const srcDirectory = 'src';
const englishTranslationFiles = 'src/utils/lang/en.json';
const jsxTsxFileExtensions = ['.js', '.jsx', '.tsx'];
const searchPattern = /(?<![a-zA-Z])t\('[^']+'\)/g;
const extractedValues = {};

// extract all translation in the t() formatter from frontend
// and add them in lang/en.json

function extractValueFromPattern(pattern) {
  const match = /(?<![a-zA-Z])t\('([^']+)'\)/.exec(pattern);
  return match ? match[1] : null;
}

async function extractI18nValues(directory) {
  try {
    const files = await readdir(directory);
    for (const file of files) {
      const filePath = path.join(directory, file);
      // eslint-disable-next-line no-await-in-loop
      const stats = await stat(filePath);

      if (stats.isDirectory()) {
        // eslint-disable-next-line no-await-in-loop
        await extractI18nValues(filePath); // Recursively call the function for directories
      } else if (stats.isFile() && jsxTsxFileExtensions.includes(path.extname(filePath))) {
        // eslint-disable-next-line no-await-in-loop
        const data = await readFile(filePath, 'utf8');
        const matches = data.match(searchPattern);

        if (matches) {
          matches.forEach((match) => {
            const value = extractValueFromPattern(match);
            if (value) {
              extractedValues[value] = value;
            }
          });
        }
      }
    }
  } catch (error) {
    // eslint-disable-next-line no-console
    console.error(`Error: ${error.message}`);
  }
}

async function mergeWithExistingData() {
  try {
    const existingData = await readFile(englishTranslationFiles, 'utf8');
    const existingValues = JSON.parse(existingData);

    const updatedValues = { ...existingValues };

    // Append only the new values that do not already exist in the file
    // eslint-disable-next-line no-console
    console.log('--- Add Frontend new key ---');
    for (const key in extractedValues) {
      if (!Object.prototype.hasOwnProperty.call(updatedValues, key)) {
        // eslint-disable-next-line no-console
        console.log(key);
        updatedValues[key] = extractedValues[key];
      }
    }
    // eslint-disable-next-line no-console
    console.log('--- End ---');
    // Write the merged values back to the file
    const sortedKeys = Object.keys(updatedValues).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
    const sortedValues = {};
    sortedKeys.forEach((key) => {
      sortedValues[key] = updatedValues[key];
    });
    await writeFile(englishTranslationFiles, JSON.stringify(sortedValues, null, 2));
    // eslint-disable-next-line no-console
    console.log('File written successfully');
  } catch (error) {
    // eslint-disable-next-line no-console
    console.error(`Error merging with existing data: ${error.message}`);
  }
}

async function main() {
  // eslint-disable-next-line no-console
  console.log('--- extract i18n values from frontend ---');
  await extractI18nValues(srcDirectory);
  await mergeWithExistingData();
}

main();
