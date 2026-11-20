import { describe, expect, it } from 'vitest';

import { blockText, fieldText } from '../../../../../admin/components/threat_arsenal/customization/fieldUpdates';
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

describe('blockText', () => {
  const field = (name: PayloadFieldUpdateOutput['field_name'], value: string): PayloadFieldUpdateOutput => ({
    field_name: name,
    field_block: 'EXECUTION',
    field_filigran: value,
    field_status: 'CONFLICT',
  });

  it('given_oneField_should_readAsItsValue', () => {
    // Act & Assert
    expect(blockText([field('COMMAND_CONTENT', 'whoami')], f => f.field_filigran, f => f.field_name)).toBe('whoami');
  });

  it('given_severalFields_should_readEachUnderItsLabel', () => {
    // Act
    const text = blockText([field('COMMAND_CONTENT', 'whoami'), field('CLEANUP_COMMAND', 'del out.txt')], f => f.field_filigran, f => f.field_name);

    // Assert
    expect(text).toBe('COMMAND_CONTENT\nwhoami\n\nCLEANUP_COMMAND\ndel out.txt');
  });
});
