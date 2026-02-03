import react from '@vitejs/plugin-react';
import path from 'path';
import { defineConfig, loadEnv } from 'vite';

const basePath = '';

const backProxy = () => ({
  target: 'http://localhost:8080',
  changeOrigin: true,
  ws: true,
});

export default ({ mode }: { mode: string }) => {
  process.env = {
    ...process.env,
    ...loadEnv(mode, process.cwd()),
  };

  // https://vitejs.dev/config/
  return defineConfig({
    build: {
      target: ['chrome58'],
      sourcemap: true,
      outDir: 'builder/prod/build',
    },

    experimental: {
      renderBuiltUrl(filename, { hostId }) {
        if (path.extname(hostId) === '.js') {
          return { runtime: `window.__assetsPath(${JSON.stringify(filename)})` };
        } else if (hostId === 'index.html') {
          return `%BASE_PATH%/${filename}`;
        }
        return { relative: true };
      },
    },

    publicDir: 'src/static/public',

    resolve: {
      extensions: ['.tsx', '.ts', '.jsx', '.js', '.json'],
      conditions: ['mui-modern', 'module', 'browser', 'development|production'],
    },

    plugins: [
      {
        name: 'html-transform',
        enforce: 'pre',
        apply: 'serve',
        transformIndexHtml(html) {
          return html.replace(/%BASE_PATH%/g, basePath)
            .replace(/%APP_TITLE%/g, 'OpenAEV Dev')
            .replace(/%APP_DESCRIPTION%/g, 'OpenAEV Development platform')
            .replace(/%APP_FAVICON%/g, `${basePath}/static/favicon.png`)
            .replace(/%APP_MANIFEST%/g, `${basePath}/static/manifest.json`);
        },
      },
      react(),
    ],

    server: {
      port: 3001,
      proxy: {
        '/api': backProxy(),
        '/login': backProxy(),
        '/logout': backProxy(),
        '/oauth2': backProxy(),
        '/saml2': backProxy(),
        '/actuator': backProxy(),
      },
    },
  });
};
