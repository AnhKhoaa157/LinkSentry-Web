export const LOCALES = ['en', 'vi'] as const;

export type Locale = (typeof LOCALES)[number];

export function isLocale(value: string): value is Locale {
  return (LOCALES as readonly string[]).includes(value);
}

/** Browser language is only a first-run default; an explicit stored choice always wins. */
export function getBrowserLocale(): Locale {
  if (typeof navigator === 'undefined') {
    return 'en';
  }

  const candidates = navigator.languages.length > 0 ? navigator.languages : [navigator.language];
  return candidates.some((language) => language.toLowerCase().startsWith('vi')) ? 'vi' : 'en';
}
