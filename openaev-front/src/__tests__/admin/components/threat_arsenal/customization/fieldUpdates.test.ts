import { describe, expect, it } from 'vitest';

import { fieldText } from '../../../../../admin/components/threat_arsenal/customization/fieldUpdates';
import { type PayloadFieldUpdateOutput } from '../../../../../utils/api-types';

describe('fieldText', () => {
  it.each<[string, PayloadFieldUpdateOutput['field_name'], unknown, string]>([
    ['an empty value', 'COMMAND_CONTENT', null, ''],
    ['a command', 'COMMAND_CONTENT', 'whoami /all', 'whoami /all'],
    ['platforms', 'PLATFORMS', ['Linux', 'Windows'], 'Linux\nWindows'],
    ['arguments', 'ARGUMENTS', [{
      key: 'user',
      default_value: 'admin',
    }], 'user = admin'],
    ['prerequisites', 'PREREQUISITES', [{
      executor: 'psh',
      get_command: 'Get-Module',
    }], 'psh: Get-Module'],
  ])('given_%s_should_readAsText', (_, field, value, text) => {
    // Act & Assert
    expect(fieldText(field, value)).toBe(text);
  });
});
