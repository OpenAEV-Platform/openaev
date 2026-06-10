import { de, enUS, es, fr, it, ja, ko, ru, zhCN } from 'date-fns/locale';

import enOpenAEV from './lang/en.json';

export type LanguageCode = 'de' | 'en' | 'es' | 'fr' | 'it' | 'ja' | 'ko' | 'ru' | 'zh';

// OAEV translation catalogs. Only the default locale (en) is bundled eagerly; the other locales
// are code-split and loaded on demand via loadLocaleMessages, then cached in this map so
// synchronous consumers (utils/Action.ts) can read the active catalog.
export const oaevLocaleMap: Partial<Record<LanguageCode, Record<string, string>>> & { en: Record<string, string> } = { en: enOpenAEV };

const localeLoaders: Record<LanguageCode, () => Promise<{ default: Record<string, string> }>> = {
  de: () => import('./lang/de.json'),
  en: () => Promise.resolve({ default: enOpenAEV }),
  es: () => import('./lang/es.json'),
  fr: () => import('./lang/fr.json'),
  it: () => import('./lang/it.json'),
  ja: () => import('./lang/ja.json'),
  ko: () => import('./lang/ko.json'),
  ru: () => import('./lang/ru.json'),
  zh: () => import('./lang/zh.json'),
};

export const loadLocaleMessages = async (lang: LanguageCode): Promise<Record<string, string>> => {
  const cached = oaevLocaleMap[lang];
  if (cached) {
    return cached;
  }
  const loader = localeLoaders[lang];
  if (!loader) {
    return oaevLocaleMap.en;
  }
  try {
    const messages = (await loader()).default;
    oaevLocaleMap[lang] = messages;
    return messages;
  } catch {
    // Locale chunk failed to load (network error, missing asset, ...): degrade to the
    // always-bundled english catalog instead of leaving the UI without translations
    return oaevLocaleMap.en;
  }
};

// Date-fns locale map
export const dateFnsLocaleMap = {
  de,
  en: enUS,
  es,
  fr,
  it,
  ja,
  ko,
  ru,
  zh: zhCN,
};

// Moment locale map
export const momentMap: Record<LanguageCode, string> = {
  de: 'de-de',
  en: 'en-us',
  es: 'es-es',
  fr: 'fr-fr',
  it: 'it-it',
  ja: 'ja-jp',
  ko: 'ko-kr',
  ru: 'ru-ru',
  zh: 'zh-cn',
};
