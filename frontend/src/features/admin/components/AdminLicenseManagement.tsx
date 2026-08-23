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
import { localizeApiError, localizeValidationMessage } from '@/lib/i18n/messages';
import type { Translate } from '@/lib/i18n/LocaleContext';
import type { TranslationKey } from '@/lib/i18n/translations';
import { useLocale } from '@/lib/i18n/useLocale';

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
  const { locale, t } = useLocale();

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

  async function invalidateLicenseQueries() {
    await queryClient.invalidateQueries({ queryKey: adminLicensesQueryKey });
  }

  const createMutation = useMutation({
    mutationFn: createAdminLicense,
    onSuccess: async (license) => {
      await invalidateLicenseQueries();
      selectLicense(license.licenseId, license.expiresAt);
      setCreateLabel('');
      setCreateExpiryDate('');
      setCreateMaxDevices('');
      setFeedback({ kind: 'success', message: t('admin.management.feedbackCreated') });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error, t) }),
  });

  const grantMutation = useMutation({
    mutationFn: ({ licenseId, code }: { readonly licenseId: string; readonly code: string }) =>
      grantAdminDevice(licenseId, { activationCode: code }),
    onSuccess: async (license) => {
      await invalidateLicenseQueries();
      setGrantCode('');
      setGrantFormError(null);
      selectLicense(license.licenseId, license.expiresAt);
      setFeedback({ kind: 'success', message: t('admin.management.feedbackGranted') });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error, t) }),
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
      setFeedback({ kind: 'success', message: t('admin.management.feedbackExtended') });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error, t) }),
  });

  const revokeLicenseMutation = useMutation({
    mutationFn: revokeAdminLicense,
    onSuccess: async () => {
      await invalidateLicenseQueries();
      setFeedback({ kind: 'success', message: t('admin.management.feedbackLicenseRevoked') });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error, t) }),
  });

  const revokeDeviceMutation = useMutation({
    mutationFn: revokeAdminDevice,
    onSuccess: async () => {
      await invalidateLicenseQueries();
      if (deviceLookupCode !== null) {
        await queryClient.invalidateQueries({ queryKey: ['admin', 'device', deviceLookupCode] });
      }
      setFeedback({ kind: 'success', message: t('admin.management.feedbackDeviceRevoked') });
    },
    onError: (error) => setFeedback({ kind: 'error', message: safeErrorMessage(error, t) }),
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
      setCreateFormError(localizeValidationMessage(t, parsed.error.issues[0]?.message));
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
      setGrantFormError(localizeValidationMessage(t, parsed.error.issues[0]?.message));
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
      setExtendFormError(localizeValidationMessage(t, parsed.error.issues[0]?.message));
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
      setLookupFormError(localizeValidationMessage(t, parsed.error.issues[0]?.message));
      return;
    }
    setDeviceLookupCode(parsed.data.activationCode);
  }

  function confirmLicenseRevoke(license: LicenseResponse) {
    if (window.confirm(t('admin.confirm.revokeLicense', { label: license.label }))) {
      setFeedback(null);
      revokeLicenseMutation.mutate(license.licenseId);
    }
  }

  function confirmDeviceRevoke(deviceId: string) {
    if (window.confirm(t('admin.confirm.revokeDevice'))) {
      setFeedback(null);
      revokeDeviceMutation.mutate(deviceId);
    }
  }

  return (
    <div className="space-y-6">
      {feedback ? (
        <p
          role={feedback.kind === 'error' ? 'alert' : 'status'}
          className={feedback.kind === 'error' ? 'ui-alert p-3 text-sm' : 'ui-success text-sm font-bold'}
        >
          {feedback.message}
        </p>
      ) : null}

      <Card title={t('admin.management.title')} description={t('admin.management.description')}>
        <form onSubmit={handleCreate} noValidate className="surface-inset space-y-5 p-4 sm:p-5">
          <h3 className="text-content-primary text-sm font-bold">{t('admin.management.createTitle')}</h3>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="ui-label sm:col-span-2" htmlFor="create-license-label">
              {t('admin.management.label')}
              <input
                id="create-license-label"
                value={createLabel}
                onChange={(event) => setCreateLabel(event.target.value)}
                maxLength={200}
                required
                className="ui-input mt-2 px-3 py-2.5 font-normal"
              />
            </label>
            <label className="ui-label" htmlFor="create-license-expiry">
              {t('admin.management.expiry')} ({t('admin.management.expiryOptional')})
              <input
                id="create-license-expiry"
                type="date"
                value={createExpiryDate}
                onChange={(event) => setCreateExpiryDate(event.target.value)}
                className="ui-input mt-2 px-3 py-2.5 font-normal"
              />
              <span className="body-muted mt-1 block text-xs font-normal">
                {t('admin.management.expiryHint')}
              </span>
            </label>
            <label className="ui-label" htmlFor="create-license-max-devices">
              {t('admin.management.maxDevices')} ({t('admin.management.maxDevicesOptional')})
              <input
                id="create-license-max-devices"
                type="number"
                min={1}
                step={1}
                value={createMaxDevices}
                onChange={(event) => setCreateMaxDevices(event.target.value)}
                className="ui-input mt-2 px-3 py-2.5 font-normal"
              />
              <span className="body-muted mt-1 block text-xs font-normal">
                {t('admin.management.maxDevicesHint')}
              </span>
            </label>
          </div>
          {createFormError ? (
            <p role="alert" className="text-danger text-sm">
              {createFormError}
            </p>
          ) : null}
          <button type="submit" disabled={createMutation.isPending} className="ui-button ui-button-primary">
            {createMutation.isPending ? t('admin.management.creating') : t('admin.management.create')}
          </button>
        </form>

        <div className="mt-7">
          <h3 className="text-content-primary text-sm font-bold">{t('admin.management.allLicenses')}</h3>
          {licensesQuery.isPending ? (
            <p className="body-secondary mt-3 text-sm">{t('admin.management.loadingLicenses')}</p>
          ) : null}
          {licensesQuery.isError ? (
            <p role="alert" className="ui-alert mt-3 p-3 text-sm">
              {safeErrorMessage(licensesQuery.error, t)}
            </p>
          ) : null}
          {licensesQuery.isSuccess && licensesQuery.data.length === 0 ? (
            <p className="body-secondary mt-3 text-sm">{t('admin.management.noLicenses')}</p>
          ) : null}
          {licensesQuery.isSuccess && licensesQuery.data.length > 0 ? (
            <div className="mt-3 space-y-3">
              {licensesQuery.data.map((license) => (
                <div key={license.licenseId} className="surface-inset p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h4 className="text-content-primary font-bold">{license.label}</h4>
                      <p className="body-secondary mt-1 text-sm">
                        {t('admin.management.devicesCount', {
                          active: license.activeDeviceCount,
                          max: license.maxDevices,
                          expiry: formatExpiry(license.expiresAt, locale, t),
                        })}
                      </p>
                    </div>
                    <Badge tone={license.revoked ? 'high' : 'accent'}>
                      {license.revoked ? t('admin.management.revoked') : t('admin.management.active')}
                    </Badge>
                  </div>
                  <button
                    type="button"
                    onClick={() => selectLicense(license.licenseId, license.expiresAt)}
                    className="ui-button ui-button-secondary mt-4 min-h-10 px-3 text-xs"
                    aria-label={t('admin.management.inspectAria', { label: license.label })}
                  >
                    {t('admin.management.inspect')}
                  </button>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      </Card>

      <Card title={t('admin.management.detailsTitle')} description={t('admin.management.detailsDescription')}>
        {selectedLicenseId === null ? (
          <p className="body-secondary text-sm">{t('admin.management.selectPrompt')}</p>
        ) : null}
        {selectedLicenseQuery.isPending ? (
          <p className="body-secondary text-sm">{t('admin.management.loadingDetails')}</p>
        ) : null}
        {selectedLicenseQuery.isError ? (
          <p role="alert" className="ui-alert p-3 text-sm">
            {safeErrorMessage(selectedLicenseQuery.error, t)}
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
        title={t('admin.management.activationsTitle')}
        description={t('admin.management.activationsDescription')}
      >
        <form
          onSubmit={handleDeviceLookup}
          noValidate
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
        >
          <label className="ui-label flex-1" htmlFor="device-activation-code">
            {t('admin.management.activationCode')}
            <input
              id="device-activation-code"
              value={deviceCodeInput}
              onChange={(event) => setDeviceCodeInput(event.target.value)}
              maxLength={32}
              required
              className="ui-input mt-2 px-3 py-2.5 font-mono font-normal"
              placeholder={t('admin.management.activationPlaceholder')}
            />
          </label>
          <button type="submit" disabled={deviceQuery.isFetching} className="ui-button ui-button-secondary">
            {t('admin.management.inspectDevice')}
          </button>
        </form>
        {lookupFormError ? (
          <p role="alert" className="text-danger mt-3 text-sm">
            {lookupFormError}
          </p>
        ) : null}
        {deviceLookupCode !== null && deviceQuery.isPending ? (
          <p className="body-secondary mt-4 text-sm">{t('admin.management.loadingDevice')}</p>
        ) : null}
        {deviceQuery.isError ? (
          <p role="alert" className="ui-alert mt-4 p-3 text-sm">
            {safeErrorMessage(deviceQuery.error, t)}
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
  const { locale, t } = useLocale();

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-content-primary text-lg font-bold">{license.label}</h3>
          <p className="body-secondary mt-1 text-sm">
            {t('admin.management.created')} {formatDate(license.createdAt, locale)} ·{' '}
            {formatExpiry(license.expiresAt, locale, t)}
          </p>
        </div>
        <Badge tone={license.revoked ? 'high' : 'accent'}>
          {license.revoked ? t('admin.management.revoked') : t('admin.management.active')}
        </Badge>
      </div>

      <section aria-labelledby="attached-devices-heading">
        <h4 id="attached-devices-heading" className="text-content-primary text-sm font-bold">
          {t('admin.management.attachedDevices')}
        </h4>
        {license.devices.length === 0 ? (
          <p className="body-secondary mt-2 text-sm">{t('admin.management.noDevices')}</p>
        ) : (
          <ul className="mt-2 space-y-2">
            {license.devices.map((device) => (
              <li key={device.deviceId} className="surface-inset p-3 text-sm">
                <span className="text-content-primary font-mono font-bold">{device.activationCode}</span>
                <span className="body-secondary ml-2">
                  {device.clientLabel ?? t('admin.management.unlabelledDevice')}
                </span>
                <span className="body-muted mt-1 block text-xs">
                  {t('admin.management.granted', { date: formatDate(device.grantedAt, locale) })}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <form onSubmit={onGrant} noValidate className="surface-inset space-y-3 p-4">
        <h4 className="text-content-primary text-sm font-bold">{t('admin.management.grantTitle')}</h4>
        <label className="ui-label" htmlFor="grant-device-code">
          {t('admin.management.pastedCode')}
          <input
            id="grant-device-code"
            value={grantCode}
            onChange={(event) => setGrantCode(event.target.value)}
            maxLength={32}
            required
            className="ui-input mt-2 px-3 py-2.5 font-mono font-normal"
          />
        </label>
        {grantFormError ? (
          <p role="alert" className="text-danger text-sm">
            {grantFormError}
          </p>
        ) : null}
        <button
          type="submit"
          disabled={isGrantPending || license.revoked}
          className="ui-button ui-button-primary"
        >
          {isGrantPending ? t('admin.management.granting') : t('admin.management.grant')}
        </button>
      </form>

      <form onSubmit={onExtend} noValidate className="surface-inset space-y-3 p-4">
        <h4 className="text-content-primary text-sm font-bold">{t('admin.management.changeExpiry')}</h4>
        <label className="ui-label" htmlFor="extend-license-expiry">
          {t('admin.management.newExpiry')}
          <input
            id="extend-license-expiry"
            type="date"
            value={extendExpiryDate}
            disabled={extendNoExpiry}
            onChange={(event) => setExtendExpiryDate(event.target.value)}
            className="ui-input mt-2 px-3 py-2.5 font-normal"
          />
        </label>
        <label className="body-secondary flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={extendNoExpiry}
            onChange={(event) => setExtendNoExpiry(event.target.checked)}
          />
          {t('admin.management.noExpiryOption')}
        </label>
        {extendFormError ? (
          <p role="alert" className="text-danger text-sm">
            {extendFormError}
          </p>
        ) : null}
        <button type="submit" disabled={isExtendPending} className="ui-button ui-button-secondary">
          {isExtendPending ? t('admin.management.saving') : t('admin.management.saveExpiry')}
        </button>
      </form>

      <button
        type="button"
        onClick={onRevoke}
        disabled={isRevokePending || license.revoked}
        className="ui-button border-rose-700/60 bg-rose-50 text-rose-700 dark:bg-rose-950/30 dark:text-rose-300"
      >
        {isRevokePending
          ? t('admin.management.revoking')
          : license.revoked
            ? t('admin.management.licenseRevoked')
            : t('admin.management.revokeLicense')}
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
  const { locale, t } = useLocale();
  const stateKey: Record<DeviceLookupResponse['state'], TranslationKey> = {
    PENDING: 'admin.management.deviceState.pending',
    LICENSED: 'admin.management.deviceState.licensed',
    EXPIRED: 'admin.management.deviceState.expired',
    REVOKED: 'admin.management.deviceState.revoked',
  };

  return (
    <div className="surface-inset mt-4 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-content-primary font-bold">{device.activationCode}</h3>
          <p className="body-secondary mt-1 text-sm">
            {device.clientLabel ?? t('admin.management.unlabelledDevice')}
          </p>
        </div>
        <Badge tone={device.state === 'LICENSED' ? 'accent' : device.state === 'PENDING' ? 'muted' : 'high'}>
          {t(stateKey[device.state])}
        </Badge>
      </div>
      <dl className="body-secondary mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="body-muted text-xs font-bold tracking-wide uppercase">
            {t('admin.management.created')}
          </dt>
          <dd className="text-content-primary mt-1">{formatDate(device.createdAt, locale)}</dd>
        </div>
        <div>
          <dt className="body-muted text-xs font-bold tracking-wide uppercase">
            {t('admin.management.license')}
          </dt>
          <dd className="text-content-primary mt-1">
            {device.licenseId ?? t('admin.management.notAssigned')}
          </dd>
        </div>
      </dl>
      <button
        type="button"
        onClick={onRevoke}
        disabled={isRevoking || device.licenseId === null}
        className="ui-button mt-4 border-rose-700/60 bg-rose-50 text-rose-700 dark:bg-rose-950/30 dark:text-rose-300"
      >
        {isRevoking
          ? t('admin.management.revoking')
          : device.licenseId === null
            ? t('admin.management.noAssignment')
            : t('admin.management.revokeDevice')}
      </button>
    </div>
  );
}

function dateToInstant(value: string): string | null {
  return value === '' ? null : value + 'T23:59:59Z';
}

function toDateInputValue(value: string | null): string {
  if (value === null) {
    return '';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toISOString().slice(0, 10);
}

function formatExpiry(value: string | null, locale: 'en' | 'vi', t: Translate): string {
  return value === null
    ? t('common.noExpiry')
    : t('admin.management.expires', { date: formatDate(value, locale) });
}

function formatDate(value: string, locale: 'en' | 'vi'): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return locale === 'vi' ? 'Không có ngày' : 'Date unavailable';
  }
  return new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function safeErrorMessage(error: unknown, t: Translate): string {
  return localizeApiError(t, normalizeApiError(error));
}
