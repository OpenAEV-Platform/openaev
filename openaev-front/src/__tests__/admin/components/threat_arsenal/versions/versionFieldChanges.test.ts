import { describe, expect, it } from 'vitest';

import versionFieldChanges from '../../../../../admin/components/threat_arsenal/versions/versionFieldChanges';

describe('versionFieldChanges', () => {
  it('given_aChangedCommand_should_nameItAsTheForm', () => {
    // Act
    const rows = versionFieldChanges([{
      change_field: 'command_content',
      change_before: 'net share',
      change_after: 'net view',
    }]);

    // Assert
    expect(rows).toEqual([{
      id: 'command_content',
      field: 'Command',
      before: 'net share',
      after: 'net view',
    }]);
  });

  it('given_aFieldTheFormDoesNotShow_should_nameItFromThePayload', () => {
    // Act
    const rows = versionFieldChanges([{
      change_field: 'payload_output_parsers',
      change_before: [],
      change_after: [{ output_parser_mode: 'STDOUT' }],
    }]);

    // Assert
    expect(rows[0].field).toBe('Output parsers');
    expect(rows[0].before).toBe('');
    expect(rows[0].after).toBe('{"output_parser_mode":"STDOUT"}');
  });

  it('given_aList_should_showOneItemPerLine', () => {
    // Act
    const rows = versionFieldChanges([{
      change_field: 'payload_platforms',
      change_before: ['Windows'],
      change_after: ['Windows', 'Linux'],
    }]);

    // Assert
    expect(rows[0].after).toBe('Windows\nLinux');
  });
});
