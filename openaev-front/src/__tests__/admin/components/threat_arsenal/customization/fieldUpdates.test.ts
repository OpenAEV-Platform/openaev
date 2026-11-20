import { describe, expect, it } from 'vitest';

import { fieldText } from '../../../../../admin/components/threat_arsenal/customization/fieldUpdates';

describe('fieldText', () => {
  it.each([
    ['an empty value', null, ''],
    ['a command', 'whoami /all', 'whoami /all'],
    ['platforms', ['Linux', 'Windows'], 'Linux\nWindows'],
    ['arguments', [{
      key: 'user',
      default_value: 'admin',
    }], 'user = admin'],
    ['prerequisites', [{
      executor: 'psh',
      get_command: 'Get-Module',
    }], 'psh: Get-Module'],
  ])('given_%s_should_readAsText', (_, value, text) => {
    // Act & Assert
    expect(fieldText(value)).toBe(text);
  });
});
