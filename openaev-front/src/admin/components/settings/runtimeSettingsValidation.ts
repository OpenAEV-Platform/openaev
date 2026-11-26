import type { RuntimeSettingOutput } from '../../../utils/api-types';

/** The validation message of one integer field, or undefined when the value is valid. */
const validateRuntimeValue = (setting: RuntimeSettingOutput, raw: string, t: (key: string, values?: Record<string, string>) => string): string | undefined => {
  if (setting.type !== 'integer') {
    return undefined;
  }
  const trimmed = raw.trim();
  const value = Number(trimmed);
  if (trimmed === '' || !Number.isInteger(value) || value < (setting.min ?? 0) || value > (setting.max ?? Number.MAX_SAFE_INTEGER)) {
    return t('Must be an integer between {min} and {max}', {
      min: String(setting.min ?? 0),
      max: String(setting.max ?? ''),
    });
  }
  return undefined;
};

export default validateRuntimeValue;
