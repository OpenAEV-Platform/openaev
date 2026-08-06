import '@fontsource/ibm-plex-sans';
import '@fontsource/geologica';
import '@xyflow/react/dist/style.css';
import 'react-grid-layout/css/styles.css';
import '@filigran/chatbot/styles.css';
import '@filigran/rich-text-editor/styles.css';
import '@filigran/design-system/dist/index.css';
import './static/css/index.css';
import './static/css/design-system-host.css';

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
