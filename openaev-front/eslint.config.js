import js from '@eslint/js';
import stylistic from '@stylistic/eslint-plugin';
import vitest from '@vitest/eslint-plugin';
import i18next from 'eslint-plugin-i18next';
import playwright from 'eslint-plugin-playwright';
import react from 'eslint-plugin-react';
import { reactRefresh } from 'eslint-plugin-react-refresh';
import simpleImportSort from 'eslint-plugin-simple-import-sort';
import globals from 'globals';
import ts from 'typescript-eslint';

import { checkNodeVersion } from './scripts/check-node-version.js';

checkNodeVersion();
// imported once the version is checked: an older Node fails to load its TypeScript sources
const { default: customRules } = await import('eslint-plugin-custom-rules');

export default [
  // rules recommended by @eslint/js
  js.configs.recommended,

  // rules recommended by typescript-eslint
  ...ts.configs.recommended,

  // rules recommended by eslint-plugin-react
  react.configs.flat.recommended,
  react.configs.flat['jsx-runtime'],
  {
    settings: {
      react: {
        // temporary workaround for https://github.com/jsx-eslint/eslint-plugin-react/issues/3977
        // ('detect' crashes on ESLint 10); must follow the installed React version, which
        // Renovate keeps in step (customManagers in renovate.json5)
        version: '19.3',
      },
    },
  },

  // rules recommended by @stylistic/eslint-plugin
  stylistic.configs.customize({ semi: true }),

  // rules recommended by eslint-plugin-i18next
  i18next.configs['flat/recommended'],

  // rules of the local package eslint-plugin-custom-rules
  customRules.configs.recommended,

  // other config
  {
    plugins: {
    // eslint-plugin-react-refresh
      'react-refresh': reactRefresh.plugin,
      // eslint-plugin-simple-import-sort
      'simple-import-sort': simpleImportSort,
    },
    rules: {
    // react-refresh rules
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],

      // eslint-plugin-simple-import-sort rules
      'simple-import-sort/imports': 'error',
      'simple-import-sort/exports': 'error',

      // @typescript-eslint rules
      '@typescript-eslint/naming-convention': ['error', {
        selector: 'variable',
        format: ['camelCase', 'UPPER_CASE'],
        leadingUnderscore: 'allow',
        trailingUnderscore: 'allow',
        filter: {
          regex: '/([^_]*)/',
          match: true,
        },
      }],
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
      '@typescript-eslint/no-use-before-define': 'error',
      '@typescript-eslint/consistent-type-imports': ['error', { fixStyle: 'inline-type-imports' }],

      // eslint-plugin-react rules
      'react/prop-types': 0,

      // @stylistic rules
      '@stylistic/brace-style': ['error', '1tbs', { allowSingleLine: true }],
      '@stylistic/multiline-ternary': ['error', 'always-multiline', { ignoreJSX: true }],
      '@stylistic/object-curly-newline': ['error', { multiline: true }],
      '@stylistic/object-property-newline': ['error', { allowAllPropertiesOnSameLine: false }],

      // custom rules
      'sort-imports': 'off',
      // `import type` declarations may stay apart from the value import of the same module
      'no-duplicate-imports': ['error', { allowSeparateTypeImports: true }],
      'no-underscore-dangle': 'error',
      'no-await-in-loop': 'error',
      'no-param-reassign': 'error',
      'consistent-return': 'error',
      'default-case': 'error',
      'no-template-curly-in-string': 'error',
      'no-bitwise': 'error',
      'no-nested-ternary': 'error',
      'prefer-promise-reject-errors': 'error',
      'no-console': 'error',
      'max-len': [
        'error', 180, 2, {
          ignoreUrls: true,
          ignoreComments: false,
          ignoreRegExpLiterals: true,
          ignoreStrings: true,
          ignoreTemplateLiterals: true,
        },
      ],
      'no-restricted-imports': [
        'error', {
          patterns: [
            {
              group: [
                '@mui/material/*', '!@mui/material/locale', '!@mui/material/styles', '!@mui/material/colors', '!@mui/material/transitions',
                '@mui/icons-material/*',
                '@mui/lab/*',
              ],
              message: 'Please use named import from @mui/* instead.',
            },
          ],
          paths: [
            {
              name: 'react',
              importNames: ['*'],
              message: 'Do not use `import * as React from \'react\'`. Use `import {...} from \'react\'` instead.',
            },
          ],
        },
      ],
    },
    files: ['**/*.js', '**/*.ts', '**/*.jsx', '**/*.tsx'],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.commonjs,
        ...globals.es2020,
        process: true,
      },
    },
  },

  // unit tests config
  {
    files: ['src/__tests__/**/*'],
    // rules recommended by vitest
    plugins: { vitest },
    rules: {
      ...vitest.configs.recommended.rules,
      // The design system assertions live in shared helpers, which the rule cannot see by itself.
      'vitest/expect-expect': [
        'error',
        { assertFunctionNames: ['expect', 'expectLibrary*', 'expectNoMuiControls'] },
      ],
    },
  },

  // tests e2e config
  {
    files: ['tests_e2e/**/*'],
    // rules recommended by eslint-plugin-playwright
    ...playwright.configs['flat/recommended'],
  },

  // ignores patterns
  {
    ignores: [
      'node_modules',
      'coverage',
      'public',
      'src/static/ext',
      'builder/prod/build',
      '__generated__',
      // fds-migration bridge (filigran-design-system pnpm generate:mui-bridge) —
      // pure generated data, regenerate upstream instead of hand-fixing lint
      // errors here (OpenCTI pilot hit 1216 avoidable errors by skipping this).
      'src/components/fds-tokens.generated.ts',
      'src/components/fds-tokens.generated.meta.json',
      'test-results',
      'playwright-report',
      'blob-report',
      'playwright/.cache',
      '.yarn',
    ],
  },
];
