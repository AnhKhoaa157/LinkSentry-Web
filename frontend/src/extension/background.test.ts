import { afterEach, describe, expect, it, vi } from 'vitest';

const MENU_ID = 'linksentry-check-link';
const LINK = 'https://login.example.com.security-check.invalid/account?token=query-sentinel';

type InstalledListener = () => void;
type ClickListener = (info: chrome.contextMenus.OnClickData) => void;
type ChangedListener = (changes: Record<string, { newValue?: unknown }>, areaName: string) => void;

function stubChrome(options: { openPopupFails?: boolean; storedLocale?: string } = {}) {
  const listeners: { installed?: InstalledListener; clicked?: ClickListener; changed?: ChangedListener } = {};
  const session = new Map<string, unknown>();
  const chromeStub = {
    runtime: {
      onInstalled: { addListener: vi.fn((listener: InstalledListener) => (listeners.installed = listener)) },
    },
    storage: {
      local: {
        get: vi.fn(async (key: string) => (options.storedLocale ? { [key]: options.storedLocale } : {})),
      },
      session: {
        set: vi.fn(async (items: Record<string, unknown>) => {
          for (const [key, value] of Object.entries(items)) {
            session.set(key, value);
          }
        }),
      },
      onChanged: { addListener: vi.fn((listener: ChangedListener) => (listeners.changed = listener)) },
    },
    contextMenus: {
      removeAll: vi.fn().mockResolvedValue(undefined),
      create: vi.fn(),
      update: vi.fn().mockResolvedValue(undefined),
      onClicked: { addListener: vi.fn((listener: ClickListener) => (listeners.clicked = listener)) },
    },
    action: {
      openPopup: options.openPopupFails
        ? vi.fn().mockRejectedValue(new Error('Cannot open the popup here'))
        : vi.fn().mockResolvedValue(undefined),
      setBadgeText: vi.fn().mockResolvedValue(undefined),
      setBadgeBackgroundColor: vi.fn().mockResolvedValue(undefined),
    },
  };
  vi.stubGlobal('chrome', chromeStub);
  return { chromeStub, listeners, session };
}

async function loadWorker() {
  vi.resetModules();
  await import('@/extension/background');
}

function click(listeners: { clicked?: ClickListener }, menuItemId: string, linkUrl: string) {
  listeners.clicked?.({ menuItemId, linkUrl, editable: false } as chrome.contextMenus.OnClickData);
}

describe('extension service worker', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('adds a localized "Check this link" item for http(s) links on install', async () => {
    const { chromeStub, listeners } = stubChrome({ storedLocale: 'vi' });
    await loadWorker();

    listeners.installed?.();

    await vi.waitFor(() => expect(chromeStub.contextMenus.create).toHaveBeenCalledOnce());
    expect(chromeStub.contextMenus.removeAll).toHaveBeenCalledOnce();
    expect(chromeStub.contextMenus.create).toHaveBeenCalledWith({
      id: MENU_ID,
      title: 'Kiểm tra liên kết này bằng LinkSentry',
      contexts: ['link'],
      targetUrlPatterns: ['http://*/*', 'https://*/*'],
    });
  });

  it('retitles the item when the popup language changes, and ignores other storage changes', async () => {
    const { chromeStub, listeners } = stubChrome();
    await loadWorker();

    listeners.changed?.({ 'linksentry.locale': { newValue: 'en' } }, 'local');
    listeners.changed?.({ 'linksentry.locale': { newValue: 'en' } }, 'session');
    listeners.changed?.({ 'linksentry.theme': { newValue: 'dark' } }, 'local');

    expect(chromeStub.contextMenus.update).toHaveBeenCalledOnce();
    expect(chromeStub.contextMenus.update).toHaveBeenCalledWith(MENU_ID, {
      title: 'Check this link with LinkSentry',
    });
  });

  it('parks the chosen link and opens the popup', async () => {
    const { chromeStub, listeners, session } = stubChrome();
    await loadWorker();

    click(listeners, MENU_ID, LINK);

    await vi.waitFor(() => expect(chromeStub.action.openPopup).toHaveBeenCalledOnce());
    await vi.waitFor(() => expect(session.get('linksentry.pendingLinkScan')).toMatchObject({ url: LINK }));
    expect(chromeStub.action.setBadgeText).not.toHaveBeenCalled();
  });

  it('falls back to a toolbar badge when the popup cannot open by itself', async () => {
    const { chromeStub, listeners, session } = stubChrome({ openPopupFails: true });
    await loadWorker();

    click(listeners, MENU_ID, LINK);

    await vi.waitFor(() => expect(chromeStub.action.setBadgeText).toHaveBeenCalledWith({ text: '1' }));
    expect(session.get('linksentry.pendingLinkScan')).toMatchObject({ url: LINK });
  });

  it('ignores other menu items and links that are not http(s)', async () => {
    const { chromeStub, listeners, session } = stubChrome();
    await loadWorker();

    click(listeners, 'some-other-item', LINK);
    click(listeners, MENU_ID, 'javascript:alert(1)');
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(session.size).toBe(0);
    expect(chromeStub.action.openPopup).not.toHaveBeenCalled();
  });

  it('never writes the link to the console', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((method) =>
      vi.spyOn(console, method),
    );
    const { chromeStub, listeners } = stubChrome({ openPopupFails: true });
    await loadWorker();

    click(listeners, MENU_ID, LINK);
    await vi.waitFor(() => expect(chromeStub.action.setBadgeText).toHaveBeenCalled());

    for (const spy of spies) {
      for (const call of spy.mock.calls) {
        expect(call.map(String).join(' ')).not.toContain('query-sentinel');
      }
    }
  });
});
