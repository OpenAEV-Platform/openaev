import { defineConfig } from 'knip/config';

export default defineConfig({
  ignoreDependencies: [
    // Run as a CLI from a command string in scripts/auto-translate.js, which knip cannot parse.
    'i18n-auto-translation',
  ],
  ignoreBinaries: [
    // Windows shell built-in used by the show-report-e2e script, not a package binary.
    'start',
  ],
});
