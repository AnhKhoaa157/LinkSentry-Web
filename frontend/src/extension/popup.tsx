import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { Popup } from '@/extension/components/Popup';
import { createQueryClient } from '@/lib/api/queryClient';
import '@/app/styles.css';
import '@/extension/popup.css';

const container = document.getElementById('root');

if (!container) {
  throw new Error('Root element #root is missing from popup.html');
}

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={createQueryClient()}>
      <Popup />
    </QueryClientProvider>
  </StrictMode>,
);
