import { describe, expect, it } from 'vitest';

import markingDefinitionImportReasons from '../../../../../admin/components/settings/marking_definitions/marking-definition-import-reasons';
import { type ImportRowReason } from '../../../../../components/common/import/import-report-types';
import { type Translate } from '../../../../../components/i18n';
import en from '../../../../../utils/lang/en.json';

// Fills the placeholders like the formatter does, and refuses a key the language files lack: a
// sentence nobody translated would reach the user as a raw key.
const t = ((message: string, values?: Record<string, string>): string => {
  if (!(message in en)) throw new Error(`Missing translation key: ${message}`);
  return message.replace(/\{(\w+)\}/g, (_, name: string) => values?.[name] ?? `{${name}}`);
}) as Translate;

const reasons = markingDefinitionImportReasons(t);

const messageOf = (reason: ImportRowReason, params: Record<string, string> = {}) => reasons[reason].message(params);

describe('markingDefinitionImportReasons', () => {
  it.each<ImportRowReason>(['MISSING_FIELD', 'VALUE_TOO_LONG', 'INVALID_VALUE', 'CONFLICT', 'INSUFFICIENT_ACCESS'])(
    'labels %s with a translated label',
    (reason) => {
      // Act & Assert
      expect(reasons[reason].label).toBeTruthy();
    },
  );

  it('shows a conflict and an insufficient access in the medium tone, the others in the default one', () => {
    // Act & Assert
    expect(reasons.CONFLICT.severity).toBe('medium');
    expect(reasons.INSUFFICIENT_ACCESS.severity).toBe('medium');
    expect(reasons.MISSING_FIELD.severity).toBeUndefined();
    expect(reasons.VALUE_TOO_LONG.severity).toBeUndefined();
    expect(reasons.INVALID_VALUE.severity).toBeUndefined();
  });

  it.each<[ImportRowReason, Record<string, string>, string]>([
    ['MISSING_FIELD', {}, 'A value is missing: type, definition, color and order are all required.'],
    ['VALUE_TOO_LONG', { max_length: '255' }, 'The type and the definition must have 255 characters at most.'],
    ['INVALID_VALUE', { column: 'color' }, 'The color must be a hex value such as #4CAF50.'],
    ['INVALID_VALUE', { column: 'order' }, 'The order must be a whole number, 0 or more.'],
    ['INSUFFICIENT_ACCESS', {
      order: '4',
      highest_order: '3',
    }, 'Its order (4) is above the highest order you hold for its type (3).'],
    ['INSUFFICIENT_ACCESS', {}, 'You hold no marking of its type.'],
  ])('writes the sentence of %s from its params', (reason, params, expected) => {
    // Act
    const message = messageOf(reason, params);

    // Assert
    expect(message).toBe(expected);
  });

  describe('a conflict', () => {
    it('names the existing marking and the color that differs', () => {
      // Act
      const message = messageOf('CONFLICT', {
        color: '#ff0000',
        known_color: '#ffc107',
      });

      // Assert
      expect(message).toBe('The color differs: this row has #ff0000, the existing marking has #ffc107.');
    });

    it('names the earlier row of the file and the order that differs', () => {
      // Act
      const message = messageOf('CONFLICT', {
        earlier_row: '1',
        order: '5',
        known_order: '2',
      });

      // Assert
      expect(message).toBe('The order differs: this row has 5, row 1 of the file has 2.');
    });

    it('gives one sentence for each value that differs', () => {
      // Act
      const message = messageOf('CONFLICT', {
        color: '#00ff00',
        known_color: '#ffc107',
        order: '2',
        known_order: '3',
      });

      // Assert
      expect(message).toBe('The color differs: this row has #00ff00, the existing marking has #ffc107. '
        + 'The order differs: this row has 2, the existing marking has 3.');
    });
  });

  it('writes nothing for an invalid value in a column it does not know', () => {
    // Act & Assert
    expect(messageOf('INVALID_VALUE', { column: 'type' })).toBeNull();
    expect(messageOf('INVALID_VALUE')).toBeNull();
  });
});
