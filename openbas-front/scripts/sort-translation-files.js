import fs from 'node:fs';
import path from 'node:path';

function sortJSONKeys(json) {
  const sortedKeys = Object.keys(json).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
  const sortedJSON = {};
  sortedKeys.forEach((key) => {
    sortedJSON[key] = json[key];
  });
  return sortedJSON;
}

function sortJSONFile(filePath) {
  try {
    // Read JSON file
    const jsonData = JSON.parse(fs.readFileSync(filePath, 'utf8'));

    // Sort JSON keys
    const sortedJSON = sortJSONKeys(jsonData);

    // Write back to the file
    fs.writeFileSync(filePath, JSON.stringify(sortedJSON, null, 2));

    // eslint-disable-next-line no-console
    console.log(`JSON file "${filePath}" has been sorted successfully.`);
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error(`Error sorting JSON file "${filePath}":`, err);
  }
}

function sortAllJSONFiles(dirPath) {
  try {
    // Get list of files in directory
    const files = fs.readdirSync(dirPath);

    // Iterate through files
    files.forEach((file) => {
      const filePath = path.join(dirPath, file);

      // Skip if not a JSON file or if it's en.json
      if (!file.endsWith('.json')) {
        return;
      }

      // Sort JSON file
      sortJSONFile(filePath);
    });
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error('Error reading directory:', err);
  }
}

sortAllJSONFiles('src/utils/lang');
