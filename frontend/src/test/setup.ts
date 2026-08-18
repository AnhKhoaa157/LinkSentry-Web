import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

// Unmount between tests so a leftover tree cannot satisfy the next test's query.
afterEach(() => {
  cleanup();
  sessionStorage.clear();
  vi.clearAllMocks();
});

beforeEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});
