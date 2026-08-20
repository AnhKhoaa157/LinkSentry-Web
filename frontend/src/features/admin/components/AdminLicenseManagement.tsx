import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import {
  createAdminLicense,
  extendAdminLicense,
  grantAdminDevice,
  revokeAdminDevice,
  revokeAdminLicense,
} from '@/features/admin/api/adminLicenses';
import {
  adminLicensesQueryKey,
  useAdminDeviceQuery,
  useAdminLicenseQuery,
  useAdminLicensesQuery,
} from '@/features/admin/api/useAdminLicenseQueries';
import {
  createLicenseRequestSchema,
  extendLicenseRequestSchema,
  grantDeviceRequestSchema,
  type DeviceLookupResponse,
  type LicenseResponse,
} from '@/features/admin/schemas/adminLicense';
import { normalizeApiError } from '@/lib/api/errors';

interface Feedback {
  readonly kind: 'success' | 'error';
  readonly message: string;
}

/** The protected administrator workflow for licenses and public device activations. */
export function AdminLicenseManagement() {
  const queryClient = useQueryClient();
  const licensesQuery = useAdminLicensesQuery();
  const [selectedLicenseId, setSelectedLicenseId] = useState<string | null>(null);
  const selectedLicenseQuery = useAdminLicenseQuery(selectedLicenseId);
  const [deviceCodeInput, setDeviceCodeInput] = useState('');
  const [deviceLookupCode, setDeviceLookupCode] = useState<string | null>(null);
  const deviceQuery = useAdminDeviceQuery(deviceLookupCode);

  const [createLabel, setCreateLabel] = useState('');
  const [createExpiryDate, setCreateExpiryDate] = useState('');
  const [createMaxDevices, setCreateMaxDevices] = useState('');
  const [createFormError, setCreateFormError] = useState<string | null>(null);
  const [grantCode, setGrantCode] = useState('');
  const [grantFormError, setGrantFormError] = useState<string | null>(null);
  const [extendExpiryDate, setExtendExpiryDate] = useState('');
  const [extendNoExpiry, setExtendNoExpiry] = useState(false);
  const [extendFormError, setExtendFormError] = useState<string | null>(null);
  const [lookupFormError, setLookupFormError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  function selectLicense(licenseId: string, expiresAt: string | null) {
    setSelectedLicenseId(licenseId);
    setExtendNoExpiry(expiresAt === null);
    setExtendExpiryDate(toDateInputValue(expiresAt));
  }

  const invalidateLicenseQueries = async () => {
    await queryClient.invalidateQueries({ queryKey: adminLicensesQueryKey });
  };

  const createMutation = useMutation({
    mutationFn: createAdminLicense,
    onSuccess: async (license) => {
      await invalidateLicenseQueries();
      selectLicense(license.licenseId, license.expiresAt);
      setCreateLabel('');
      setCreateExpiryDate('');
      setCreateMaxDevices('');
      setFeedback({ kind: 'success', message: 'License created.' });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error) }),
  });

  const grantMutation = useMutation({
    mutationFn: ({ licenseId, code }: { readonly licenseId: string; readonly code: string }) =>
      grantAdminDevice(licenseId, { activationCode: code }),
    onSuccess: async (license) => {
      await invalidateLicenseQueries();
      setGrantCode('');
      setGrantFormError(null);
      selectLicense(license.licenseId, license.expiresAt);
      setFeedback({ kind: 'success', message: 'Device granted to the license.' });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error) }),
  });

  const extendMutation = useMutation({
    mutationFn: ({
      licenseId,
      expiresAt,
    }: {
      readonly licenseId: string;
      readonly expiresAt: string | null;
    }) => extendAdminLicense(licenseId, { expiresAt }),
    onSuccess: async (license) => {
      await invalidateLicenseQueries();
      selectLicense(license.licenseId, license.expiresAt);
      setFeedback({ kind: 'success', message: 'License expiry updated.' });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error) }),
  });

  const revokeLicenseMutation = useMutation({
    mutationFn: revokeAdminLicense,
    onSuccess: async () => {
      await invalidateLicenseQueries();
      setFeedback({ kind: 'success', message: 'License revoked.' });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error) }),
  });

  const revokeDeviceMutation = useMutation({
    mutationFn: revokeAdminDevice,
    onSuccess: async () => {
      await invalidateLicenseQueries();
      if (deviceLookupCode !== null) {
        await queryClient.invalidateQueries({ queryKey: ['admin', 'device', deviceLookupCode] });
      }
      setFeedback({ kind: 'success', message: 'Device revoked.' });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error) }),
  });

  function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateFormError(null);
    setFeedback(null);
    const base = { label: createLabel, expiresAt: dateToInstant(createExpiryDate) };
    const candidate =
      createMaxDevices.trim() === '' ? base : { ...base, maxDevices: Number(createMaxDevices) };
    const parsed = createLicenseRequestSchema.safeParse(candidate);
    if (!parsed.success) {
      setCreateFormError(parsed.error.issues[0]?.message ?? 'Check the license details and try again.');
      return;
    }
    createMutation.mutate(parsed.data);
  }

  function handleGrant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setGrantFormError(null);
    setFeedback(null);
    if (!selectedLicenseId) {
      return;
    }
    const parsed = grantDeviceRequestSchema.safeParse({ activationCode: grantCode });
    if (!parsed.success) {
      setGrantFormError(parsed.error.issues[0]?.message ?? 'Enter a valid activation code.');
      return;
    }
    grantMutation.mutate({ licenseId: selectedLicenseId, code: parsed.data.activationCode });
  }

  function handleExtend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setExtendFormError(null);
    setFeedback(null);
    if (!selectedLicenseId) {
      return;
    }
    const parsed = extendLicenseRequestSchema.safeParse({
      expiresAt: extendNoExpiry ? null : dateToInstant(extendExpiryDate),
    });
    if (!parsed.success) {
      setExtendFormError(parsed.error.issues[0]?.message ?? 'Choose an expiry date or no expiry.');
      return;
    }
    extendMutation.mutate({ licenseId: selectedLicenseId, expiresAt: parsed.data.expiresAt });
  }

  function handleDeviceLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLookupFormError(null);
    setFeedback(null);
    const parsed = grantDeviceRequestSchema.safeParse({ activationCode: deviceCodeInput });
    if (!parsed.success) {
      setLookupFormError(parsed.error.issues[0]?.message ?? 'Enter a valid activation code.');
      return;
    }
    setDeviceLookupCode(parsed.data.activationCode);
  }

  function confirmLicenseRevoke(license: LicenseResponse) {
    if (window.confirm(`Revoke the license "${license.label}"? Its devices will lose access.`)) {
      setFeedback(null);
      revokeLicenseMutation.mutate(license.licenseId);
    }
  }

  function confirmDeviceRevoke(deviceId: string) {
    if (window.confirm('Revoke this device assignment?')) {
      setFeedback(null);
      revokeDeviceMutation.mutate(deviceId);
    }
  }

  return (
    <div className="mt-6 space-y-6">
      {feedback ? (
        <p
          role={feedback.kind === 'error' ? 'alert' : 'status'}
          className={feedback.kind === 'error' ? 'text-sm text-rose-400' : 'text-sm text-emerald-400'}
        >
          {feedback.message}
        </p>
      ) : null}

      <Card title="Licenses" description="Create, inspect, and manage administrator-granted device access.">
        <form
          onSubmit={handleCreate}
          noValidate
          className="border-ink-800 bg-ink-950/40 space-y-4 rounded-lg border p-4"
        >
          <h3 className="text-ink-100 text-sm font-semibold">Create a license</h3>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="text-ink-100 text-sm font-medium sm:col-span-2" htmlFor="create-license-label">
              Label
              <input
                id="create-license-label"
                value={createLabel}
                onChange={(event) => setCreateLabel(event.target.value)}
                maxLength={200}
                required
                className="border-ink-800 bg-ink-900 text-ink-100 mt-2 w-full rounded-lg border px-3 py-2 font-normal"
              />
            </label>
            <label className="text-ink-100 text-sm font-medium" htmlFor="create-license-expiry">
              Expiry (optional)
              <input
                id="create-license-expiry"
                type="date"
                value={createExpiryDate}
                onChange={(event) => setCreateExpiryDate(event.target.value)}
                className="border-ink-800 bg-ink-900 text-ink-100 mt-2 w-full rounded-lg border px-3 py-2 font-normal"
              />
              <span className="text-ink-400 mt-1 block text-xs font-normal">Leave blank for no expiry.</span>
            </label>
            <label className="text-ink-100 text-sm font-medium" htmlFor="create-license-max-devices">
              Maximum devices (optional)
              <input
                id="create-license-max-devices"
                type="number"
                min={1}
                step={1}
                value={createMaxDevices}
                onChange={(event) => setCreateMaxDevices(event.target.value)}
                className="border-ink-800 bg-ink-900 text-ink-100 mt-2 w-full rounded-lg border px-3 py-2 font-normal"
              />
              <span className="text-ink-400 mt-1 block text-xs font-normal">
                Blank uses the backend default.
              </span>
            </label>
          </div>
          {createFormError ? (
            <p role="alert" className="text-sm text-rose-400">
              {createFormError}
            </p>
          ) : null}
          <button
            type="submit"
            disabled={createMutation.isPending}
            className="bg-accent-500 text-ink-950 rounded-lg px-4 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
          >
            {createMutation.isPending ? 'Creating…' : 'Create license'}
          </button>
        </form>

        <div className="mt-6">
          <h3 className="text-ink-100 text-sm font-semibold">All licenses</h3>
          {licensesQuery.isPending ? <p className="text-ink-300 mt-3 text-sm">Loading licenses…</p> : null}
          {licensesQuery.isError ? (
            <p role="alert" className="mt-3 text-sm text-rose-400">
              {safeErrorMessage(licensesQuery.error)}
            </p>
          ) : null}
          {licensesQuery.isSuccess && licensesQuery.data.length === 0 ? (
            <p className="text-ink-300 mt-3 text-sm">No licenses yet. Create one above to get started.</p>
          ) : null}
          {licensesQuery.isSuccess && licensesQuery.data.length > 0 ? (
            <div className="mt-3 space-y-3">
              {licensesQuery.data.map((license) => (
                <div key={license.licenseId} className="border-ink-800 bg-ink-950/30 rounded-lg border p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h4 className="text-ink-100 font-medium">{license.label}</h4>
                      <p className="text-ink-300 mt-1 text-sm">
                        {license.activeDeviceCount} of {license.maxDevices} devices ·{' '}
                        {formatExpiry(license.expiresAt)}
                      </p>
                    </div>
                    <Badge tone={license.revoked ? 'high' : 'accent'}>
                      {license.revoked ? 'Revoked' : 'Active'}
                    </Badge>
                  </div>
                  <button
                    type="button"
                    onClick={() => selectLicense(license.licenseId, license.expiresAt)}
                    className="border-ink-700 bg-ink-900 text-ink-100 mt-4 rounded-lg border px-3 py-2 text-sm font-medium"
                    aria-label={`Inspect license ${license.label}`}
                  >
                    Inspect license
                  </button>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      </Card>

      <Card
        title="License details"
        description="Inspect attached devices, change expiry, or revoke a license."
      >
        {selectedLicenseId === null ? (
          <p className="text-ink-300 text-sm">Select a license above to inspect it.</p>
        ) : null}
        {selectedLicenseQuery.isPending ? (
          <p className="text-ink-300 text-sm">Loading license details…</p>
        ) : null}
        {selectedLicenseQuery.isError ? (
          <p role="alert" className="text-sm text-rose-400">
            {safeErrorMessage(selectedLicenseQuery.error)}
          </p>
        ) : null}
        {selectedLicenseQuery.data ? (
          <LicenseDetail
            license={selectedLicenseQuery.data}
            grantCode={grantCode}
            setGrantCode={setGrantCode}
            grantFormError={grantFormError}
            extendExpiryDate={extendExpiryDate}
            setExtendExpiryDate={setExtendExpiryDate}
            extendNoExpiry={extendNoExpiry}
            setExtendNoExpiry={setExtendNoExpiry}
            extendFormError={extendFormError}
            onGrant={handleGrant}
            onExtend={handleExtend}
            onRevoke={() => confirmLicenseRevoke(selectedLicenseQuery.data)}
            isGrantPending={grantMutation.isPending}
            isExtendPending={extendMutation.isPending}
            isRevokePending={revokeLicenseMutation.isPending}
          />
        ) : null}
      </Card>

      <Card
        title="Device activations"
        description="Inspect a public activation code before granting or revoking it."
      >
        <form
          onSubmit={handleDeviceLookup}
          noValidate
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
        >
          <label className="text-ink-100 flex-1 text-sm font-medium" htmlFor="device-activation-code">
            Activation code
            <input
              id="device-activation-code"
              value={deviceCodeInput}
              onChange={(event) => setDeviceCodeInput(event.target.value)}
              maxLength={32}
              required
              className="border-ink-800 bg-ink-900 text-ink-100 mt-2 w-full rounded-lg border px-3 py-2 font-mono font-normal"
              placeholder="K7H9-QX3P"
            />
          </label>
          <button
            type="submit"
            disabled={deviceQuery.isFetching}
            className="border-ink-700 bg-ink-900 text-ink-100 rounded-lg border px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50"
          >
            Inspect device
          </button>
        </form>
        {lookupFormError ? (
          <p role="alert" className="mt-3 text-sm text-rose-400">
            {lookupFormError}
          </p>
        ) : null}
        {deviceLookupCode !== null && deviceQuery.isPending ? (
          <p className="text-ink-300 mt-4 text-sm">Loading device…</p>
        ) : null}
        {deviceQuery.isError ? (
          <p role="alert" className="mt-4 text-sm text-rose-400">
            {safeErrorMessage(deviceQuery.error)}
          </p>
        ) : null}
        {deviceQuery.data ? (
          <DeviceDetail
            device={deviceQuery.data}
            onRevoke={() => confirmDeviceRevoke(deviceQuery.data.deviceId)}
            isRevoking={revokeDeviceMutation.isPending}
          />
        ) : null}
      </Card>
    </div>
  );
}

interface LicenseDetailProps {
  readonly license: LicenseResponse;
  readonly grantCode: string;
  readonly setGrantCode: (value: string) => void;
  readonly grantFormError: string | null;
  readonly extendExpiryDate: string;
  readonly setExtendExpiryDate: (value: string) => void;
  readonly extendNoExpiry: boolean;
  readonly setExtendNoExpiry: (value: boolean) => void;
  readonly extendFormError: string | null;
  readonly onGrant: (event: FormEvent<HTMLFormElement>) => void;
  readonly onExtend: (event: FormEvent<HTMLFormElement>) => void;
  readonly onRevoke: () => void;
  readonly isGrantPending: boolean;
  readonly isExtendPending: boolean;
  readonly isRevokePending: boolean;
}

function LicenseDetail({
  license,
  grantCode,
  setGrantCode,
  grantFormError,
  extendExpiryDate,
  setExtendExpiryDate,
  extendNoExpiry,
  setExtendNoExpiry,
  extendFormError,
  onGrant,
  onExtend,
  onRevoke,
  isGrantPending,
  isExtendPending,
  isRevokePending,
}: LicenseDetailProps) {
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-ink-100 text-lg font-semibold">{license.label}</h3>
          <p className="text-ink-300 mt-1 text-sm">
            Created {formatDate(license.createdAt)} · {formatExpiry(license.expiresAt)}
          </p>
        </div>
        <Badge tone={license.revoked ? 'high' : 'accent'}>{license.revoked ? 'Revoked' : 'Active'}</Badge>
      </div>

      <div>
        <h4 className="text-ink-100 text-sm font-semibold">Attached devices</h4>
        {license.devices.length === 0 ? (
          <p className="text-ink-300 mt-2 text-sm">No devices are attached to this license.</p>
        ) : (
          <ul className="mt-2 space-y-2">
            {license.devices.map((device) => (
              <li
                key={device.deviceId}
                className="border-ink-800 bg-ink-950/30 rounded-lg border p-3 text-sm"
              >
                <span className="text-ink-100 font-mono">{device.activationCode}</span>
                <span className="text-ink-300 ml-2">{device.clientLabel ?? 'Unlabelled device'}</span>
                <span className="text-ink-400 mt-1 block text-xs">
                  Granted {formatDate(device.grantedAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <form
        onSubmit={onGrant}
        noValidate
        className="border-ink-800 bg-ink-950/30 space-y-3 rounded-lg border p-4"
      >
        <h4 className="text-ink-100 text-sm font-semibold">Grant a device</h4>
        <label className="text-ink-100 block text-sm font-medium" htmlFor="grant-device-code">
          Pasted activation code
          <input
            id="grant-device-code"
            value={grantCode}
            onChange={(event) => setGrantCode(event.target.value)}
            maxLength={32}
            required
            className="border-ink-800 bg-ink-900 text-ink-100 mt-2 w-full rounded-lg border px-3 py-2 font-mono font-normal"
          />
        </label>
        {grantFormError ? (
          <p role="alert" className="text-sm text-rose-400">
            {grantFormError}
          </p>
        ) : null}
        <button
          type="submit"
          disabled={isGrantPending || license.revoked}
          className="bg-accent-500 text-ink-950 rounded-lg px-4 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isGrantPending ? 'Granting…' : 'Grant device'}
        </button>
      </form>

      <form
        onSubmit={onExtend}
        noValidate
        className="border-ink-800 bg-ink-950/30 space-y-3 rounded-lg border p-4"
      >
        <h4 className="text-ink-100 text-sm font-semibold">Change expiry</h4>
        <label className="text-ink-100 block text-sm font-medium" htmlFor="extend-license-expiry">
          New expiry date
          <input
            id="extend-license-expiry"
            type="date"
            value={extendExpiryDate}
            disabled={extendNoExpiry}
            onChange={(event) => setExtendExpiryDate(event.target.value)}
            className="border-ink-800 bg-ink-900 text-ink-100 mt-2 w-full rounded-lg border px-3 py-2 font-normal disabled:opacity-50"
          />
        </label>
        <label className="text-ink-300 flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={extendNoExpiry}
            onChange={(event) => setExtendNoExpiry(event.target.checked)}
          />
          No expiry
        </label>
        {extendFormError ? (
          <p role="alert" className="text-sm text-rose-400">
            {extendFormError}
          </p>
        ) : null}
        <button
          type="submit"
          disabled={isExtendPending}
          className="border-ink-700 bg-ink-900 text-ink-100 rounded-lg border px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isExtendPending ? 'Saving…' : 'Save expiry'}
        </button>
      </form>

      <button
        type="button"
        onClick={onRevoke}
        disabled={isRevokePending || license.revoked}
        className="rounded-lg border border-rose-700/60 bg-rose-950/30 px-4 py-2 text-sm font-medium text-rose-300 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {isRevokePending ? 'Revoking…' : license.revoked ? 'License revoked' : 'Revoke license'}
      </button>
    </div>
  );
}

function DeviceDetail({
  device,
  onRevoke,
  isRevoking,
}: {
  readonly device: DeviceLookupResponse;
  readonly onRevoke: () => void;
  readonly isRevoking: boolean;
}) {
  return (
    <div className="border-ink-800 bg-ink-950/30 mt-4 rounded-lg border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-ink-100 font-semibold">{device.activationCode}</h3>
          <p className="text-ink-300 mt-1 text-sm">{device.clientLabel ?? 'Unlabelled device'}</p>
        </div>
        <Badge tone={device.state === 'LICENSED' ? 'accent' : device.state === 'PENDING' ? 'muted' : 'high'}>
          {device.state}
        </Badge>
      </div>
      <dl className="text-ink-300 mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-ink-400 text-xs uppercase">Created</dt>
          <dd className="text-ink-100 mt-1">{formatDate(device.createdAt)}</dd>
        </div>
        <div>
          <dt className="text-ink-400 text-xs uppercase">License</dt>
          <dd className="text-ink-100 mt-1">{device.licenseId ?? 'Not assigned'}</dd>
        </div>
      </dl>
      <button
        type="button"
        onClick={onRevoke}
        disabled={isRevoking || device.licenseId === null}
        className="mt-4 rounded-lg border border-rose-700/60 bg-rose-950/30 px-4 py-2 text-sm font-medium text-rose-300 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {isRevoking ? 'Revoking…' : device.licenseId === null ? 'No active assignment' : 'Revoke device'}
      </button>
    </div>
  );
}

function dateToInstant(value: string): string | null {
  return value === '' ? null : `${value}T23:59:59Z`;
}

function toDateInputValue(value: string | null): string {
  if (value === null) {
    return '';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toISOString().slice(0, 10);
}

function formatExpiry(value: string | null): string {
  return value === null ? 'No expiry' : `Expires ${formatDate(value)}`;
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'Date unavailable';
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function safeErrorMessage(error: unknown): string {
  return normalizeApiError(error).message;
}
