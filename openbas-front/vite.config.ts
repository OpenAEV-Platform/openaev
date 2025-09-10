import react from '@vitejs/plugin-react';
import { createLogger, defineConfig, loadEnv, transformWithEsbuild } from 'vite';

const logger = createLogger();
const loggerError = logger.error;

logger.error = (msg, options) => {
  // Ignore jsx syntax error as it taken into account in a custom plugin
  if (msg.includes('The JSX syntax extension is not currently enabled')) return;
  loggerError(msg, options);
};

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
      minify: false,
    },

    resolve: {
      extensions: ['.js', '.tsx', '.ts', '.jsx', '.json'],
      conditions: ['mui-modern', 'module', 'browser', 'development|production'],
    },

    optimizeDeps: {
      entries: [
        './src/**/*.{js,tsx,ts,jsx}',
      ],
      include: [
        '@ckeditor/ckeditor5-react',
        '@dagrejs/dagre',
        '@hookform/resolvers/zod',
        '@microsoft/fetch-event-source',
        '@mui/icons-material',
        '@mui/lab',
        '@mui/material/colors',
        '@mui/material/styles',
        '@mui/material/transitions',
        '@uiw/react-md-editor/nohighlight',
        'ckeditor5',
        'ckeditor5/translations/en.js',
        'ckeditor5/translations/fr.js',
        'ckeditor5/translations/zh.js',
        'classcat',
        'cronstrue',
        'd3-hierarchy',
        'date-fns',
        'date-fns/locale',
        'dompurify',
        'filigran-icon',
        'final-form-arrays',
        '@hello-pangea/dnd',
        'html-react-parser',
        'js-file-download',
        'mdi-material-ui',
        'qs',
        'react-apexcharts',
        'react-color',
        'react-csv',
        'react-final-form',
        'react-final-form-arrays',
        'react-grid-layout',
        'react-hook-form',
        'react-markdown',
        'remark-flexible-markers',
        '@xyflow/react',
        'd3-hierarchy',
        '@dagrejs/dagre',
        'react-syntax-highlighter',
        'react-syntax-highlighter/dist/esm/styles/prism',
        'remark-flexible-markers',
        'remark-gfm',
        'remark-parse',
        'usehooks-ts',
        'uuid',
        'zod',
        'zod/v4',
        'zustand/shallow',
      ],
    },

    customLogger: logger,

    plugins: [
      {
        name: 'html-transform',
        enforce: 'pre',
        apply: 'serve',
        transformIndexHtml(html) {
          return html.replace(/%BASE_PATH%/g, basePath)
            .replace(/%APP_TITLE%/g, 'OpenBAS Dev')
            .replace(/%APP_DESCRIPTION%/g, 'OpenBAS Development platform')
            .replace(/%APP_FAVICON%/g, `${basePath}/src/static/ext/favicon.png`)
            .replace(/%APP_MANIFEST%/g, `${basePath}/src/static/ext/manifest.json`);
        },
      },
      {
        name: 'treat-js-files-as-jsx',
        async transform(code, id) {
          if (!id.match(/src\/.*\.js$/)) return null;
          // Use the exposed transform from vite, instead of directly
          // transforming with esbuild
          return transformWithEsbuild(code, id, {
            loader: 'jsx',
            jsx: 'automatic',
          });
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
      },
    },
  });
};
