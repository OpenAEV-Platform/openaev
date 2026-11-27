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

// Ramda is being removed, one pull request per family of functions. Files that still import it are
// listed here: a pull request that cleans a file removes it from the list, so no new import comes in.
const legacyRamdaFiles = [
  'src/admin/components/common/injects/InjectDistributionByTeam.tsx',
  'src/admin/components/common/injects/InjectDistributionByType.tsx',
  'src/admin/components/integrations/injectors/injector_contracts/CreateInjectorContract.jsx',
  'src/admin/components/settings/attack_patterns/CreateAttackPattern.jsx',
  'src/admin/components/settings/groups/tenant_groups/GroupPopover.jsx',
  'src/admin/components/settings/tags/CreateTag.jsx',
  'src/admin/components/settings/tags/TagPopover.jsx',
  'src/admin/components/simulations/simulation/injects/QuickInject.jsx',
  'src/admin/components/simulations/simulation/logs/LogPopover.jsx',
  'src/admin/components/simulations/simulation/mails/MailDistributionByInject.tsx',
  'src/admin/components/simulations/simulation/mails/MailDistributionByPlayer.tsx',
  'src/admin/components/simulations/simulation/mails/MailDistributionByTeam.tsx',
  'src/admin/components/simulations/simulation/mails/MailDistributionOverTimeChart.tsx',
  'src/admin/components/simulations/simulation/mails/MailDistributionOverTimeLine.tsx',
  'src/admin/components/simulations/simulation/overview/DistributionUtils.ts',
  'src/admin/components/simulations/simulation/overview/ExerciseDistributionScoreByTeamInPercentage.tsx',
  'src/admin/components/simulations/simulation/overview/ExerciseDistributionScoreOverTimeByInjectorContract.tsx',
  'src/admin/components/simulations/simulation/overview/ExerciseDistributionScoreOverTimeByTeam.tsx',
  'src/admin/components/simulations/simulation/overview/ExerciseDistributionScoreOverTimeByTeamInPercentage.tsx',
  'src/admin/components/simulations/simulation/validation/Validations.jsx',
  'src/admin/components/teams/organizations/OrganizationPopover.jsx',
  'src/admin/components/teams/utils.ts',
  'src/components/CountryField.jsx',
  'src/components/ItemBoolean.jsx',
  'src/components/ItemNumberDifference.jsx',
  'src/components/KillChainPhaseField.jsx',
  'src/components/OldAttackPatternField.jsx',
  'src/components/OrganizationField.jsx',
  'src/components/TagField.jsx',
  'src/store.ts',
  'src/utils/Action.ts',
  'src/utils/BrowserLanguage.ts',
];

// The code is moving from JavaScript to TypeScript, one pull request per folder. The `.js` and `.jsx`
// files that still exist under `src` are listed here: a pull request that renames a file to `.ts` or
// `.tsx` removes it from the list, so no new JavaScript file comes in. Never add a file to this list.
const legacyJsFiles = [
  'src/actions/AskAI.js',
  'src/actions/Collector.js',
  'src/actions/Comcheck.js',
  'src/actions/Communication.js',
  'src/actions/Evaluation.js',
  'src/actions/Grant.js',
  'src/actions/Group.js',
  'src/actions/KillChainPhase.js',
  'src/actions/Lessons.js',
  'src/actions/Log.js',
  'src/actions/Objective.js',
  'src/actions/Organization.js',
  'src/actions/Schema.js',
  'src/actions/channels/article-action.js',
  'src/actions/channels/channel-action.js',
  'src/actions/users/User.js',
  'src/admin/components/common/injects/InjectorContract.jsx',
  'src/admin/components/components/channels/ChannelIcon.jsx',
  'src/admin/components/components/documents/Documents.jsx',
  'src/admin/components/integrations/injectors/InjectorContracts.jsx',
  'src/admin/components/integrations/injectors/InjectorHeader.jsx',
  'src/admin/components/integrations/injectors/injector_contracts/CreateInjectorContract.jsx',
  'src/admin/components/integrations/injectors/injector_contracts/InjectorContractCustomForm.jsx',
  'src/admin/components/integrations/injectors/injector_contracts/InjectorContractPopover.jsx',
  'src/admin/components/profile/Index.jsx',
  'src/admin/components/profile/PasswordForm.jsx',
  'src/admin/components/profile/ProfileForm.jsx',
  'src/admin/components/settings/attack_patterns/AttackPatternForm.jsx',
  'src/admin/components/settings/attack_patterns/AttackPatternPopover.jsx',
  'src/admin/components/settings/attack_patterns/AttackPatterns.jsx',
  'src/admin/components/settings/attack_patterns/CreateAttackPattern.jsx',
  'src/admin/components/settings/groups/Groups.jsx',
  'src/admin/components/settings/groups/tenant_groups/GroupPopover.jsx',
  'src/admin/components/settings/tags/CreateTag.jsx',
  'src/admin/components/settings/tags/TagForm.jsx',
  'src/admin/components/settings/tags/TagPopover.jsx',
  'src/admin/components/settings/tags/Tags.jsx',
  'src/admin/components/simulations/simulation/chat/Chat.jsx',
  'src/admin/components/simulations/simulation/injects/QuickInject.jsx',
  'src/admin/components/simulations/simulation/logs/LogForm.jsx',
  'src/admin/components/simulations/simulation/logs/LogPopover.jsx',
  'src/admin/components/simulations/simulation/logs/Logs.jsx',
  'src/admin/components/simulations/simulation/mails/Communication.jsx',
  'src/admin/components/simulations/simulation/mails/CommunicationForm.jsx',
  'src/admin/components/simulations/simulation/mails/Mails.jsx',
  'src/admin/components/simulations/simulation/validation/Validations.jsx',
  'src/admin/components/teams/organizations/OrganizationForm.jsx',
  'src/admin/components/teams/organizations/OrganizationPopover.jsx',
  'src/components/Autocomplete.jsx',
  'src/components/CheckboxField.jsx',
  'src/components/CountryField.jsx',
  'src/components/CustomTooltip.jsx',
  'src/components/DeprecatedColorPickerField.jsx',
  'src/components/Empty.jsx',
  'src/components/FileField.jsx',
  'src/components/ItemBoolean.jsx',
  'src/components/ItemNumberDifference.jsx',
  'src/components/ItemTags.jsx',
  'src/components/KillChainPhaseField.jsx',
  'src/components/Message.jsx',
  'src/components/OldAttackPatternField.jsx',
  'src/components/OrganizationField.jsx',
  'src/components/TagField.jsx',
  'src/components/TruncatedText.jsx',
  'src/components/common/Accordion.jsx',
  'src/components/fields/OldRichTextField.jsx',
  'src/components/fields/OldSelectField.jsx',
  'src/components/fields/OldSwitchField.jsx',
  'src/components/fields/OldTextField.jsx',
  'src/components/fields/SelectField.jsx',
  'src/components/fields/SimpleRichTextField.jsx',
  'src/components/fields/SliderField.jsx',
  'src/public/components/channels/Channel.jsx',
  'src/public/components/channels/ChannelMicroblogging.jsx',
  'src/public/components/channels/ChannelNewspaper.jsx',
  'src/public/components/channels/ChannelPlayer.jsx',
  'src/public/components/channels/ChannelPreview.jsx',
  'src/public/components/channels/ChannelTvChannel.jsx',
  'src/public/components/comcheck/Comcheck.jsx',
  'src/public/components/lessons/LessonsPlayer.jsx',
  'src/public/components/lessons/LessonsPreview.jsx',
  'src/public/components/login/Reset.jsx',
  'src/reducers/App.js',
  'src/utils/SortingFiltering.jsx',
  'src/utils/hooks/useDataLoader.js',
];

const restrictedImports = ({ allowRamda, extraPaths = [] }) => [
  'error', {
    patterns: [
      {
        group: [
          '@mui/material/*', '!@mui/material/locale', '!@mui/material/styles', '!@mui/material/colors', '!@mui/material/transitions',
          '@mui/icons-material/*',
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
      ...(allowRamda
        ? []
        : [{
            name: 'ramda',
            message: 'Ramda is being removed: use native JavaScript instead.',
          }]),
      ...extraPaths,
    ],
  },
];

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
      'no-restricted-imports': restrictedImports({ allowRamda: false }),
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

  // files that still import Ramda (a later config replaces the rule's options, it does not merge them)
  {
    files: legacyRamdaFiles,
    rules: { 'no-restricted-imports': restrictedImports({ allowRamda: true }) },
  },

  // no new JavaScript file under `src`: a later config replaces the rule's options, it does not merge them,
  // and no other config sets `no-restricted-syntax`, so this one stands for every file it matches
  {
    files: ['src/**/*.js', 'src/**/*.jsx'],
    ignores: legacyJsFiles,
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: 'Program',
          message: 'New JavaScript files are not allowed in src: write the file in TypeScript (.ts or .tsx).',
        },
      ],
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

  // e2e specs log in through the persona fixture; no project sets storageState, so a spec on the
  // base `test` would run logged out (a later config replaces the rule's options, it does not merge them)
  {
    files: ['tests_e2e/tests/**/*'],
    rules: {
      'no-restricted-imports': restrictedImports({
        allowRamda: false,
        extraPaths: [{
          name: '@playwright/test',
          importNames: ['test'],
          message: 'Import `test` from tests_e2e/fixtures: its persona fixture logs the test in (admin by default).',
        }],
      }),
    },
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
