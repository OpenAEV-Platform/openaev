import { describe, expect, it } from 'vitest';

import validateRuntimeValue from '../../../../admin/components/settings/runtimeSettingsValidation';
import type { RuntimeSettingOutput } from '../../../../utils/api-types';

const t = (key: string, values?: Record<string, string>) => Object.entries(values ?? {}).reduce((text, [k, v]) => text.replace(`{${k}}`, v), key);

const threshold: RuntimeSettingOutput = {
  key: 'inject_execution_threshold_minutes',
  value: '10',
  source: 'default',
  env_var: 'INJECT_EXECUTION_THRESHOLD_MINUTES',
  locked: false,
  type: 'integer',
  min: 1,
  max: 1440,
  enterprise_only: false,
};

describe('validateRuntimeValue', () => {
  it('accepts an integer in range', () => {
    expect(validateRuntimeValue(threshold, '60', t)).toBeUndefined();
    expect(validateRuntimeValue(threshold, '1', t)).toBeUndefined();
    expect(validateRuntimeValue(threshold, '1440', t)).toBeUndefined();
  });

  it('refuses an empty, decimal or out-of-range value with the bounds', () => {
    expect(validateRuntimeValue(threshold, '', t)).toBe('Must be an integer between 1 and 1440');
    expect(validateRuntimeValue(threshold, '2.5', t)).toBe('Must be an integer between 1 and 1440');
    expect(validateRuntimeValue(threshold, '0', t)).toBe('Must be an integer between 1 and 1440');
    expect(validateRuntimeValue(threshold, '1441', t)).toBe('Must be an integer between 1 and 1440');
  });

  it('never validates a boolean setting as a number', () => {
    expect(validateRuntimeValue({
      ...threshold,
      key: 'agent_auto_update_enabled',
      type: 'boolean',
      value: 'true',
    }, 'true', t)).toBeUndefined();
  });
});
