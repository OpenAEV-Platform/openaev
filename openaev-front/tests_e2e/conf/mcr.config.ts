import { type CoverageReportOptions } from 'monocart-coverage-reports';

// https://github.com/cenfun/monocart-coverage-reports
const coverageOptions: CoverageReportOptions = {

  name: 'OpenAEV Playwright Coverage Report',

  reports: [
    'console-details',
    'v8',
    'lcovonly',
  ],

  entryFilter: {
    '**/node_modules/**': false,
    '**/**': true,
  },

  sourceFilter: {
    '**/node_modules/**': false,
    '**/**': true,
  },

  outputDir: './test-results/coverage',
};

export default coverageOptions;
