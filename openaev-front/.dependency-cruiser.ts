// Module graph checks: import cycles, dependencies missing from package.json or
// imported from the wrong place, and production code reaching into tests.
// Run with `yarn depcruise`. Pre-existing violations are listed in
// .dependency-cruiser-known-violations.json and ignored; once one is fixed, drop it from that
// file with `yarn depcruise --baseline --baseline-mode shrink-only`.

import type { IConfiguration } from 'dependency-cruiser';

// Files allowed to import devDependencies: unit tests, e2e tests and tooling.
const DEV_FILES = [
  '\\.(test|spec)\\.(js|jsx|ts|tsx)$',
  '^src/__tests__/',
  '^tests_e2e/',
  '^vite\\.config\\.ts$',
  '^vitest\\.config\\.ts$',
  '^playwright\\.config\\.ts$',
  '^eslint\\.config\\.js$',
  '^knip\\.ts$',
  '^\\.dependency-cruiser\\.ts$',
  '^scripts/',
];

const TEST_FILES = [
  '\\.(test|spec)\\.(js|jsx|ts|tsx)$',
  '^src/__tests__/',
  '^tests_e2e/',
];

export default {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'Import cycles make module initialisation order fragile. Move the shared code into a module both sides import.',
      from: {},
      to: { circular: true },
    },
    {
      name: 'not-to-dev-dep',
      severity: 'error',
      comment: 'Production code must not import devDependencies: they are not part of the shipped bundle\'s dependency contract. Move the package to dependencies, or import it from a test or tooling file only.',
      from: { pathNot: DEV_FILES },
      to: { dependencyTypes: ['npm-dev'] },
    },
    {
      name: 'no-non-package-json',
      severity: 'error',
      comment: 'This package is not declared in package.json: it only resolves because another dependency installs it. Add it to package.json.',
      from: {},
      to: { dependencyTypes: ['npm-no-pkg', 'npm-unknown'] },
    },
    {
      name: 'not-to-unresolvable',
      severity: 'error',
      comment: 'This import does not resolve to a file or package on disk.',
      from: {},
      to: { couldNotResolve: true },
    },
    {
      name: 'not-to-test',
      severity: 'error',
      comment: 'Production code must not import unit or e2e test code.',
      from: {
        path: '^src/',
        pathNot: TEST_FILES,
      },
      to: { path: TEST_FILES },
    },
  ],
  options: {
    doNotFollow: { path: ['node_modules'] },
    exclude: {
      path: [
        '__generated__',
        '^src/static/ext/',
        '^coverage/',
        '^playwright-report/',
        '^test-results/',
      ],
    },
    moduleSystems: ['es6', 'cjs'],
    // resolve imports the way tsc does
    tsConfig: { fileName: 'tsconfig.json' },
    // analyse the transpiled code: type-only imports and specifiers are erased, so the
    // rules only see imports that exist at runtime
    tsPreCompilationDeps: false,
    enhancedResolveOptions: {
      // same lookup order as vite.config.ts, plus declaration files
      extensions: ['.tsx', '.ts', '.jsx', '.js', '.json', '.d.ts'],
      // honour package.json "exports" so ESM-only packages resolve
      exportsFields: ['exports'],
      conditionNames: ['import', 'require', 'node', 'browser', 'module', 'types', 'default'],
      mainFields: ['module', 'main', 'types', 'typings'],
    },
    skipAnalysisNotInRules: true,
  },
} satisfies IConfiguration;
