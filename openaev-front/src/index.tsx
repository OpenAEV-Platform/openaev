import '@fontsource/ibm-plex-sans';
import '@fontsource/geologica';
import '@xyflow/react/dist/style.css';
import 'react-grid-layout/css/styles.css';
import 'ckeditor5/ckeditor5.css';
import './static/css/index.css';
import './static/css/CKEditorDark.css';
import './static/css/CKEditorLight.css';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from './app';

const container = document.getElementById('root');
if (container) {
  const root = createRoot(container);
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}
