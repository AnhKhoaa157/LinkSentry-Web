import { QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  EXPLANATION_CACHE_TTL_MS,
  explanationQueryKey,
  useExplanationQuery,
} from '@/features/explanation/api/useExplanationQuery';
import { apiClient } from '@/lib/api/client';
import { createQueryClient } from '@/lib/api/queryClient';
import type { Locale } from '@/lib/i18n/locale';

const SCAN_ID = '2ce16fb9-d52d-4310-8d45-a4e48f31889e';

interface HookProps {
  readonly scanId: string | null;
  readonly locale: Locale;
  readonly requested: boolean;
  readonly isLicensed: boolean;
}

const BASE_PROPS: HookProps = { scanId: SCAN_ID, locale: 'en', requested: false, isLicensed: true };

function structuredResponse(summary = 'Advisory summary.') {
  return {
    data: {
      data: {
        riskLevel: 'HIGH',
        keyFindings: [],
        summary,
        recommendedActions: ['Verify the sender.'],
      },
    },
  };
}

function axiosErrorWithResponse(status: number, data: unknown) {
  const headers = new AxiosHeaders();
  return new AxiosError('Request failed', 'ERR_BAD_RESPONSE', { headers }, null, {
    status,
    statusText: 'Error',
    headers,
    config: { headers },
    data,
  });
}

/** A fresh `QueryClient` per call — the same unit of isolation a real page load or extension popup open gets. */
function newSessionClient() {
  return createQueryClient();
}

function wrapperFor(client: ReturnType<typeof createQueryClient>) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

function renderExplanationQuery(client: ReturnType<typeof createQueryClient>, initialProps: HookProps) {
  return renderHook((props: HookProps) => useExplanationQuery(props), {
    wrapper: wrapperFor(client),
    initialProps,
  });
}

describe('useExplanationQuery', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('makes one POST request for the same scan and locale within 10 minutes and reuses the result on reopen', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
    const client = newSessionClient();
    const { result, rerender } = renderExplanationQuery(client, BASE_PROPS);

    rerender({ ...BASE_PROPS, requested: true });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(result.current.data?.data.summary).toBe('Advisory summary.');

    // The control collapses (a fresh mount would reset `requested` to false the
    // same way) and the user asks again — still inside the 10-minute window.
    rerender({ ...BASE_PROPS, requested: false });
    rerender({ ...BASE_PROPS, requested: true });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(1); // no second network call — served from cache
  });

  it('uses separate cache entries for EN and VI, each sending its own Accept-Language header', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
    const client = newSessionClient();
    const { result, rerender } = renderExplanationQuery(client, { ...BASE_PROPS, locale: 'en' });

    rerender({ ...BASE_PROPS, locale: 'en', requested: true });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(1);

    rerender({ ...BASE_PROPS, locale: 'vi', requested: false });
    rerender({ ...BASE_PROPS, locale: 'vi', requested: true });

    await waitFor(() => expect(postSpy).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(postSpy).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining(SCAN_ID),
      undefined,
      expect.objectContaining({ headers: { 'Accept-Language': 'en' } }),
    );
    expect(postSpy).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining(SCAN_ID),
      undefined,
      expect.objectContaining({ headers: { 'Accept-Language': 'vi' } }),
    );
    expect(client.getQueryData(explanationQueryKey(SCAN_ID, 'en'))).toBeDefined();
    expect(client.getQueryData(explanationQueryKey(SCAN_ID, 'vi'))).toBeDefined();
  });

  it('calls the backend again once the 10-minute cache lifetime has passed', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
    const client = newSessionClient();
    const { result, rerender } = renderExplanationQuery(client, BASE_PROPS);

    rerender({ ...BASE_PROPS, requested: true });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(1);

    rerender({ ...BASE_PROPS, requested: false });

    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(Date.now() + EXPLANATION_CACHE_TTL_MS + 1_000);

    rerender({ ...BASE_PROPS, requested: true });

    await waitFor(() => expect(postSpy).toHaveBeenCalledTimes(2));
  });

  it('does not reuse a cached success while still inside the 10-minute window even after several reopens', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
    const client = newSessionClient();
    const { result, rerender } = renderExplanationQuery(client, BASE_PROPS);

    rerender({ ...BASE_PROPS, requested: true });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(Date.now() + EXPLANATION_CACHE_TTL_MS - 30_000); // 9m30s later

    rerender({ ...BASE_PROPS, requested: false });
    rerender({ ...BASE_PROPS, requested: true });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(1);
  });

  it('never caches a 503 provider-unavailable error as a successful response', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockRejectedValue(
      axiosErrorWithResponse(503, {
        code: 'AI_EXPLANATION_UNAVAILABLE',
        message: 'AI explanation is not available right now.',
      }),
    );
    const client = newSessionClient();
    const { result, rerender } = renderExplanationQuery(client, BASE_PROPS);

    rerender({ ...BASE_PROPS, requested: true });
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(client.getQueryData(explanationQueryKey(SCAN_ID, 'en'))).toBeUndefined();

    // A later attempt (e.g. the user clicks "Try again") must still call the
    // backend — there is nothing safe to reuse from a failed attempt.
    postSpy.mockResolvedValue(structuredResponse());
    await result.current.refetch();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(2);
  });

  it('never caches a response that fails the existing Zod schema as a successful response', async () => {
    // Missing every required field of explanationDataSchema.
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { data: {} } });
    const client = newSessionClient();
    const { result, rerender } = renderExplanationQuery(client, BASE_PROPS);

    rerender({ ...BASE_PROPS, requested: true });
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.isSuccess).toBe(false);
    expect(client.getQueryData(explanationQueryKey(SCAN_ID, 'en'))).toBeUndefined();
    expect(postSpy).toHaveBeenCalledTimes(1);
  });

  it('a request cancelled by unmounting before it resolves never blocks a later request from reaching the backend', async () => {
    let resolvePost!: (value: unknown) => void;
    const postSpy = vi.spyOn(apiClient, 'post').mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolvePost = resolve;
        }) as never,
    );
    const client = newSessionClient();
    const { result, unmount } = renderExplanationQuery(client, { ...BASE_PROPS, requested: true });

    await waitFor(() => expect(result.current.isFetching).toBe(true));
    unmount();
    resolvePost(structuredResponse());
    await new Promise((resolve) => setTimeout(resolve, 0));

    postSpy.mockResolvedValue(structuredResponse('Second, independent attempt.'));
    const second = renderExplanationQuery(client, { ...BASE_PROPS, requested: true });

    await waitFor(() => expect(second.result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(2);
    expect(second.result.current.data?.data.summary).toBe('Second, independent attempt.');
  });

  it('purges every cached explanation the moment the device stops reporting LICENSED, and never auto-refetches', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
    const client = newSessionClient();
    const { result, rerender } = renderExplanationQuery(client, BASE_PROPS);

    rerender({ ...BASE_PROPS, requested: true });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(client.getQueryData(explanationQueryKey(SCAN_ID, 'en'))).toBeDefined();

    rerender({ ...BASE_PROPS, requested: true, isLicensed: false });

    await waitFor(() => expect(client.getQueryData(explanationQueryKey(SCAN_ID, 'en'))).toBeUndefined());
    await waitFor(() => expect(result.current.isSuccess).toBe(false));
    // Losing the license purges the cache — it must not silently trigger a new
    // outbound call on its own; only an explicit user action does that.
    expect(postSpy).toHaveBeenCalledTimes(1);
  });

  it.each<'PENDING' | 'EXPIRED' | 'REVOKED'>(['PENDING', 'EXPIRED', 'REVOKED'])(
    'a %s (non-LICENSED) device cannot read a previously cached explanation',
    async () => {
      const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
      const client = newSessionClient();
      const { result, rerender } = renderExplanationQuery(client, BASE_PROPS);

      rerender({ ...BASE_PROPS, requested: true });
      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      // licensing state transitions away from LICENSED (any non-LICENSED state).
      rerender({ ...BASE_PROPS, requested: true, isLicensed: false });

      await waitFor(() => expect(result.current.data).toBeUndefined());
      expect(postSpy).toHaveBeenCalledTimes(1);
    },
  );

  it('the cache does not persist into a fresh session (a new QueryClient never has the prior data)', async () => {
    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
    const firstSessionClient = newSessionClient();
    const first = renderExplanationQuery(firstSessionClient, { ...BASE_PROPS, requested: true });

    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(firstSessionClient.getQueryData(explanationQueryKey(SCAN_ID, 'en'))).toBeDefined();

    // A fresh browser tab / extension popup open builds a brand-new QueryClient
    // (see lib/api/queryClient.ts) — nothing backs this cache beyond that instance.
    const freshSessionClient = newSessionClient();
    expect(freshSessionClient.getQueryData(explanationQueryKey(SCAN_ID, 'en'))).toBeUndefined();

    const second = renderExplanationQuery(freshSessionClient, { ...BASE_PROPS, requested: true });

    await waitFor(() => expect(second.result.current.isSuccess).toBe(true));
    expect(postSpy).toHaveBeenCalledTimes(2);
  });

  it('never writes to localStorage or sessionStorage while caching a successful response', async () => {
    const localStorageSetSpy = vi.spyOn(Storage.prototype, 'setItem');
    vi.spyOn(apiClient, 'post').mockResolvedValue(structuredResponse());
    const client = newSessionClient();
    const { result, rerender } = renderExplanationQuery(client, BASE_PROPS);

    rerender({ ...BASE_PROPS, requested: true });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(localStorageSetSpy).not.toHaveBeenCalled();
  });
});
