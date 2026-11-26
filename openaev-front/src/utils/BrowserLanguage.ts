import { DEFAULT_LANG, supportedLanguages } from '../constants/Lang';

// These window.navigator contain language information
// 1. languages -> [] of preferred languages (eg ["en-US", "zh-CN", "ja-JP"]) Firefox^32, Chrome^32
// 2. language  -> Preferred language as String (eg "en-US") Firefox^5, IE^11, Safari,
//                 Chrome sends Browser UI language
// 3. browserLanguage -> UI Language of IE
// 4. userLanguage    -> Language of Windows Regional Options
// 5. systemLanguage  -> UI Language of Windows
const browserLanguagePropertyKeys = [
  'languages',
  'language',
  'browserLanguage',
  'userLanguage',
  'systemLanguage',
];

const navigatorProperties = window.navigator as unknown as Record<string, unknown>;
const detectedLocale = browserLanguagePropertyKeys
  .map(key => navigatorProperties[key])
  .flat(Infinity) // `languages` is a list
  .filter((value): value is string => value != null)
  .map(language => language.substring(0, 2))
  .find(language => supportedLanguages.includes(language)); // first language matched

export default detectedLocale || DEFAULT_LANG; // If no locale is detected, fallback to 'en'
