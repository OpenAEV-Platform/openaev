import { DEFAULT_LANG, supportedLanguages } from '../constants/Lang';
import { pick } from './object';

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

const source: object = window.navigator;
const languageProperties = pick(source as Record<string, unknown>, browserLanguagePropertyKeys); // Get only language properties
const languageValues = Object.values(languageProperties); // Get values of the properties
const flattenedValues = languageValues.flat(Infinity); // flatten all arrays
const definedValues = flattenedValues.filter(value => value != null); // Remove null and undefined values
const languages = (definedValues as string[]).map((x: string) => x.substring(0, 2));
const detectedLocale = languages.find((x: string) => supportedLanguages.includes(x)); // Returns first language matched in languages

export default detectedLocale || DEFAULT_LANG; // If no locale is detected, fallback to 'en'
