// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-nocheck
// ts nocheck because there is an "Excessive stack depth comparing types" it seems that there is a problem with the React plugin and the defineConfig type
import react from '@vitejs/plugin-react';
// import jsdom to not let tools report them as unused
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import jsdom from 'jsdom';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['src/__tests__/**/**/*.test.{ts,tsx}'],
  },
});
