// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-nocheck
// ts nocheck because there is an "Excessive stack depth comparing types" it seems that there is a problem with the React plugin and the defineConfig type
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    // happy-dom is fully ESM-native and Node 24 compatible, replacing jsdom
    // which had a CJS/ESM conflict with its bundled parse5 dependency.
    environment: 'happy-dom',
    include: ['src/__tests__/**/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'json-summary'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/__tests__/**',
        'src/**/*.d.ts',
        'src/index.tsx',
      ],
    },
  },
});
