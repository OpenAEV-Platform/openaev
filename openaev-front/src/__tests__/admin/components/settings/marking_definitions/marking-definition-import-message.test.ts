import { describe, expect, it } from 'vitest';

import markingDefinitionImportMessage from '../../../../../admin/components/settings/marking_definitions/marking-definition-import-message';
import { type ImportReportRow } from '../../../../../utils/api-types';
import en from '../../../../../utils/lang/en.json';

// Fills the placeholders like the formatter does, and refuses a key the language files lack: a
// sentence nobody translated would reach the user as a raw key.
const t = (message: string, values?: Record<string, string>): string => {
  if (!(message in en)) throw new Error(`Missing translation key: ${message}`);
  return message.replace(/\{(\w+)\}/g, (_, name: string) => values?.[name] ?? `{${name}}`);
};

const skipped = (reason: string, params: Record<string, string> = {}): ImportReportRow => ({
  import_row_position: 3,
  import_row_status: 'SKIPPED',
  import_row_reason: reason,
  import_row_params: params,
  import_row_values: {},
});

describe('markingDefinitionImportMessage', () => {
  it.each([
    ['MISSING_FIELD', {}, 'A value is missing: type, definition, color and order are all required.'],
    ['VALUE_TOO_LONG', { max_length: '255' }, 'The type and the definition must have 255 characters at most.'],
    ['INVALID_COLOR', {}, 'The color must be a hex value such as #4CAF50.'],
    ['INVALID_ORDER', {}, 'The order must be a whole number, 0 or more.'],
    ['INSUFFICIENT_ACCESS', {
      order: '4',
      highest_order: '3',
    }, 'Its order (4) is above the highest order you hold for its type (3).'],
    ['INSUFFICIENT_ACCESS', {}, 'You hold no marking of its type.'],
  ])('writes the sentence of %s from its params', (reason, params, expected) => {
    // Act
    const message = markingDefinitionImportMessage(t, skipped(reason, params));

    // Assert
    expect(message).toBe(expected);
  });

  describe('a conflict', () => {
    it('names the existing marking and the color that differs', () => {
      // Act
      const message = markingDefinitionImportMessage(t, skipped('CONFLICT', {
        color: '#ff0000',
        known_color: '#ffc107',
      }));

      // Assert
      expect(message).toBe('The color differs: this row has #ff0000, the existing marking has #ffc107.');
    });

    it('names the earlier row of the file and the order that differs', () => {
      // Act
      const message = markingDefinitionImportMessage(t, skipped('CONFLICT', {
        earlier_row: '1',
        order: '5',
        known_order: '2',
      }));

      // Assert
      expect(message).toBe('The order differs: this row has 5, row 1 of the file has 2.');
    });

    it('gives one sentence for each value that differs', () => {
      // Act
      const message = markingDefinitionImportMessage(t, skipped('CONFLICT', {
        color: '#00ff00',
        known_color: '#ffc107',
        order: '2',
        known_order: '3',
      }));

      // Assert
      expect(message).toBe('The color differs: this row has #00ff00, the existing marking has #ffc107. '
        + 'The order differs: this row has 2, the existing marking has 3.');
    });
  });

  it('writes nothing for a reason it does not know, or none', () => {
    // Act & Assert
    expect(markingDefinitionImportMessage(t, skipped('SOMETHING_NEW'))).toBeNull();
    expect(markingDefinitionImportMessage(t, {
      ...skipped('X'),
      import_row_reason: undefined,
    })).toBeNull();
  });
});
