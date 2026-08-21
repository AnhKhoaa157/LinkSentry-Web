/**
 * English strings — the source of truth for every translation key. `vi.ts` must implement
 * exactly this key set (`Record<TranslationKey, string>` enforces that at compile time).
 *
 * Values here must stay byte-for-byte identical to the English copy that shipped before i18n
 * existed: several component tests assert this exact text, and the default locale is `'en'`.
 */
export const en = {
  'popup.eyebrow': 'Tab analysis',
  'popup.title': 'LinkSentry',
  'popup.status.checking': 'Checking this tab…',
  'popup.status.ready': 'Ready to scan the current tab.',
  'popup.status.unsupported':
    'This tab cannot be scanned. Open a regular http:// or https:// website, then reopen this popup.',
  'popup.status.rateLimited': 'Too many scan requests. Wait a moment before trying again.',
  'popup.license.panelLabel': 'License status',
  'popup.scan.button.scan': 'Scan this tab',
  'popup.scan.button.scanning': 'Scanning…',
  'popup.scan.button.scanAgain': 'Scan again',
  'popup.result.label': 'Analysis result',
  'popup.result.findingsHeading': 'Signals detected',

  'languageSwitcher.label': 'Language',
  'languageSwitcher.en': 'EN',
  'languageSwitcher.vi': 'VI',

  'license.checking': 'Checking this installation…',
  'license.state.pending': 'Trial',
  'license.state.licensed': 'Licensed',
  'license.state.expired': 'Expired',
  'license.state.revoked': 'Revoked',
  'license.description.pending':
    'This installation has not been granted a license yet. It can still scan under the free trial allowance.',
  'license.description.licensed': 'This installation currently has full access.',
  'license.description.expired':
    "This installation's license has expired. It has fallen back to the free trial allowance.",
  'license.description.revoked':
    'This installation has been revoked. It has fallen back to the free trial allowance.',
  'license.renewsOrExpires': 'Renews or expires {date}',
  'license.noExpiry': 'No expiry',
  'license.expiredOn': 'Expired {date}.',
  'license.pendingActivation.title': 'Pending activation',
  'license.pendingActivation.body':
    'Send this code to your administrator to request a license for this installation. Copying it does not by itself grant access — an administrator must attach it to a license.',
  'license.copyButton': 'Copy activation code',
  'license.copyStatus.copied': 'Copied.',
  'license.copyStatus.failed': 'Could not copy — select the code manually.',
  'license.checkAgain': 'Check status again',
  'license.checkingAgain': 'Checking…',

  'findings.empty': 'No signals were detected by the current rules. This does not mean the link is safe.',

  'riskBadge.low': 'Low risk',
  'riskBadge.moderate': 'Moderate risk',
  'riskBadge.high': 'High risk',
  'riskBadge.critical': 'Critical risk',

  'nextSteps.heading': 'Recommended next steps',
  'nextSteps.action.low':
    'No strong lexical risk signals were detected. Still verify the sender and use official channels before entering information.',
  'nextSteps.action.moderate':
    'Review the registered domain carefully. If the link arrived unexpectedly, open the official website yourself instead of using the link.',
  'nextSteps.action.high':
    'Avoid opening the link or entering credentials. Verify the request through the organization’s official app, website, or support channel.',
  'nextSteps.action.critical':
    'Do not open, sign in to, download from, or forward the link. Report it to your organization’s security or IT team.',
  'nextSteps.copyButton': 'Copy safe summary',
  'nextSteps.copySuccess': 'Summary copied.',
  'nextSteps.copyFailure': 'Could not copy the summary. Your browser blocked clipboard access.',
  'nextSteps.disclaimer':
    'The summary contains the risk level, score, registered domain, and finding titles. It never includes the submitted link.',
  'nextSteps.summary.title': 'LinkSentry link analysis',
  'nextSteps.summary.riskLevelWord.low': 'Low',
  'nextSteps.summary.riskLevelWord.moderate': 'Moderate',
  'nextSteps.summary.riskLevelWord.high': 'High',
  'nextSteps.summary.riskLevelWord.critical': 'Critical',
  'nextSteps.summary.riskLevel': 'Risk level: {level} (score {score}/100)',
  'nextSteps.summary.registeredDomain': 'Registered domain: {domain}',
  'nextSteps.summary.domainUnknown': 'not determined',
  'nextSteps.summary.findingsNone': 'Findings: none detected',
  'nextSteps.summary.findingsHeading': 'Findings:',
  'nextSteps.summary.recommendedAction': 'Recommended action: {action}',
  'nextSteps.summary.safetyNote':
    'Note: Lexical analysis inspects only the text of a link. It cannot prove that a destination is safe.',

  'explain.button.idle': 'Explain this result',
  'explain.button.loading': 'Generating explanation…',
  'explain.tryAgain': 'Try again',
  'explain.riskBadgeLabel': '{level} LEXICAL RISK',
  'explain.detectedHeading': 'What LinkSentry detected',
  'explain.noSignals':
    'No lexical signals were detected by the current rules. This does not mean the link is safe.',
  'explain.aiContextLabel': 'AI context (advisory)',
  'explain.whatToDoHeading': 'What to do',
  'explain.disclaimer':
    'This context is based on lexical signals only. LinkSentry never visits the link, so it is advisory, not a verdict.',
} as const;

export type TranslationKey = keyof typeof en;
