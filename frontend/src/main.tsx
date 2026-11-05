import '@/lib/config/zod';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from '@/app/App';
import { registerServiceWorker } from '@/pwa/registerServiceWorker';
import '@/app/styles.css';

// Production only: the dev server does not build /sw.js.
if (import.meta.env.PROD) {
  registerServiceWorker();
}

const container = document.getElementById('root');

if (!container) {
  throw new Error('Root element #root is missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
