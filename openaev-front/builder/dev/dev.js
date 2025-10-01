/* eslint import/no-extraneous-dependencies: 0 */

import { readFileSync } from 'node:fs';
import path from 'node:path';

import chokidar from 'chokidar';
import esbuild from 'esbuild';
import express from 'express';
import fsExtra from 'fs-extra/esm';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { fileURLToPath } from 'url';

// mimic CommonJS variables -- not needed if using CommonJS
// eslint-disable-next-line no-underscore-dangle
const __filename = fileURLToPath(import.meta.url);
// eslint-disable-next-line no-underscore-dangle
const __dirname = path.dirname(__filename);

const basePath = '';
const clients = [];
const buildPath = './builder/dev/build/';
const debounce = (func, timeout = 500) => {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      func.apply(this, args);
    }, timeout);
  };
};

const middleware = (target, ws = true) =>
  createProxyMiddleware({
    target: 'http://localhost:8080' + basePath + target,
    changeOrigin: true,
    ws,
  });

// Start with an initial build
esbuild
  .context({
    logLevel: 'info',
    entryPoints: ['src/index.tsx'],
    publicPath: '/',
    bundle: true,
    banner: { js: ' (() => new EventSource("http://localhost:3001/dev").onmessage = () => location.reload())();' },
    loader: {
      '.js': 'jsx',
      '.svg': 'file',
      '.png': 'file',
      '.woff': 'dataurl',
      '.woff2': 'dataurl',
      '.ttf': 'dataurl',
      '.eot': 'dataurl',
    },
    assetNames: '[dir]/[name]-[hash]',
    target: ['chrome58'],
    minify: false,
    keepNames: true,
    sourcemap: true,
    sourceRoot: 'src',
    outdir: 'builder/dev/build',
  })
  .then(async (builder) => {
    await builder.rebuild();
    // region Copy public files to build
    fsExtra.copySync('./src/static/ext', buildPath + '/static/ext', {
      recursive: true,
      overwrite: true,
    });
    // Listen change for hot recompile
    chokidar
      .watch('./src', {
        ignored: (path, stats) => stats?.isFile()
          && !(path.endsWith('.js') || path.endsWith('.jsx') || path.endsWith('.ts') || path.endsWith('.tsx')),
      })
      .on(
        'all',
        debounce(() => {
          const start = new Date().getTime();
          // eslint-disable-next-line no-console
          console.log(`[HOT RELOAD] Update of front detected`);
          return builder
            .rebuild()
            .then(() => {
              const time = new Date().getTime() - start;
              // eslint-disable-next-line no-console
              console.log(
                `[HOT RELOAD] Rebuild done in ${time} ms, updating frontend`,
              );
              clients.forEach(res => res.write('data: update\n\n'));
              clients.length = 0;
            })
            .catch((error) => {
              // eslint-disable-next-line no-console
              console.error(error);
            });
        }),
      );
    // Start a dev web server
    const app = express();
    app.get('/dev', (req, res) => {
      return clients.push(
        res.writeHead(200, {
          'Content-Type': 'text/event-stream',
          'Cache-Control': 'no-cache',
          'Access-Control-Allow-Origin': '*',
          'Connection': 'keep-alive',
        }),
      );
    });
    app.set('trust proxy', 1);
    app.use('/api', middleware('/api'));
    app.use('/login', middleware('/login'));
    app.use('/logout', middleware('/logout'));
    app.use('/oauth2', middleware('/oauth2'));
    app.use('/saml2', middleware('/saml2'));
    app.use(
      basePath + `/static`,
      express.static(path.join(__dirname, './build/static')),
    );
    app.use(`/css`, express.static(path.join(__dirname, './build')));
    app.use(`/js`, express.static(path.join(__dirname, './build')));
    app.get('*', (req, res) => {
      const data = readFileSync(`${__dirname}/index.html`, 'utf8');
      const withOptionValued = data
        .replace(/%BASE_PATH%/g, basePath)
        .replace(/%APP_TITLE%/g, 'OpenAEV Dev')
        .replace(/%APP_DESCRIPTION%/g, 'OpenAEV Development platform')
        .replace(/%APP_FAVICON%/g, `${basePath}/static/ext/favicon.png`)
        .replace(/%APP_MANIFEST%/g, `${basePath}/static/ext/manifest.json`);
      res.header(
        'Cache-Control',
        'private, no-cache, no-store, must-revalidate',
      );
      res.header('Expires', '-1');
      res.header('Pragma', 'no-cache');
      return res.send(withOptionValued);
    });
    app.listen(3001);
  });
