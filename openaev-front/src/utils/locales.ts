import { de, enUS, es, fr, it, ja, ko, ru, zhCN } from 'date-fns/locale';

import deOpenAEV from './lang/de.json';
import enOpenAEV from './lang/en.json';
import esOpenAEV from './lang/es.json';
import frOpenAEV from './lang/fr.json';
import itOpenAEV from './lang/it.json';
import jaOpenAEV from './lang/ja.json';
import koOpenAEV from './lang/ko.json';
import ruOpenAEV from './lang/ru.json';
import zhOpenAEV from './lang/zh.json';

export type LanguageCode = 'de' | 'en' | 'es' | 'fr' | 'it' | 'ja' | 'ko' | 'ru' | 'zh';

// OAEV Supported Local Language
export const oaevLocaleMap: Record<LanguageCode, Record<string, string>> = {
  de: deOpenAEV,
  en: enOpenAEV,
  es: esOpenAEV,
  fr: frOpenAEV,
  it: itOpenAEV,
  ja: jaOpenAEV,
  ko: koOpenAEV,
  ru: ruOpenAEV,
  zh: zhOpenAEV,
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
