import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

/**
 * `LicenseProvider` bootstraps or checks a device on every mount, so any test that renders a
 * component wrapped in it (every `renderWithProviders` call, by default) would otherwise make a
 * real network call and fail Zod parsing against whatever that test's own `apiClient.post`/`.get`
 * spy happens to return for an unrelated endpoint. Mocked globally here with a safe, inert
 * `PENDING` default; a test that cares about a specific license state overrides these with
 * `vi.mocked(bootstrapDevice)`/`vi.mocked(getDeviceStatus)` locally.
 */
vi.mock('@/features/license/api/device', () => ({
  bootstrapDevice: vi.fn().mockResolvedValue({
    deviceId: '00000000-0000-4000-8000-000000000000',
    activationCode: 'TEST-CODE',
    credential: 'test-device-credential',
  }),
  getDeviceStatus: vi.fn().mockResolvedValue({
    state: 'PENDING',
    activationCode: 'TEST-CODE',
    licenseExpiresAt: null,
  }),
}));

// Unmount between tests so a leftover tree cannot satisfy the next test's query.
afterEach(() => {
  cleanup();
  sessionStorage.clear();
  localStorage.clear();
  vi.clearAllMocks();
});

beforeEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});
