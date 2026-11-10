import { describe, expect, it } from 'vitest';

import { lineDiff, versionFields } from '../../../../../admin/components/threat_arsenal/approval/payloadVersionDiff';

describe('payloadVersionDiff', () => {
  describe('lineDiff', () => {
    it('keeps shared lines and marks removed and added ones in order', () => {
      // Arrange / Act
      const diff = lineDiff('whoami\nid\nhostname', 'whoami\nuname -a\nhostname');

      // Assert
      expect(diff).toEqual([
        {
          type: 'same',
          text: 'whoami',
        },
        {
          type: 'removed',
          text: 'id',
        },
        {
          type: 'added',
          text: 'uname -a',
        },
        {
          type: 'same',
          text: 'hostname',
        },
      ]);
    });

    it('treats an empty text as no line', () => {
      // Arrange / Act / Assert
      expect(lineDiff('', 'whoami')).toEqual([{
        type: 'added',
        text: 'whoami',
      }]);
      expect(lineDiff('whoami', '')).toEqual([{
        type: 'removed',
        text: 'whoami',
      }]);
    });
  });

  describe('versionFields', () => {
    it('flags the changed fields and leaves out the fields empty in both versions', () => {
      // Arrange / Act
      const fields = versionFields(
        {
          content: 'whoami',
          executor: 'sh',
          platforms: ['Windows', 'Linux'],
        },
        {
          content: 'id',
          executor: 'sh',
          platforms: ['Linux', 'Windows'],
          arguments: [{
            key: 'target',
            type: 'text',
            default_value: '10.0.0.1',
          }],
        },
      );

      // Assert
      const byKey = Object.fromEntries(fields.map(field => [field.key, field]));
      expect(Object.keys(byKey)).toEqual(['content', 'executor', 'platforms', 'elevation_required', 'arguments']);
      expect(byKey.content.changed).toBe(true);
      expect(byKey.executor.changed).toBe(false);
      expect(byKey.platforms.changed).toBe(false);
      expect(byKey.arguments.pending).toBe('target (text) = 10.0.0.1');
    });
  });
});
