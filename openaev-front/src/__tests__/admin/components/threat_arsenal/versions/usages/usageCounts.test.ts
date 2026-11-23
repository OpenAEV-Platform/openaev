import { describe, expect, it } from 'vitest';

import { olderVersionsInUse, scenarioCount, usageCount } from '../../../../../../admin/components/threat_arsenal/versions/usages/usageCounts';
import { aUsage, aVersion, someUsages } from '../../threatArsenalTestUtils';

describe('usageCounts', () => {
  describe('usageCount', () => {
    it('given shown and hidden usages should count both', () => {
      // Arrange
      const usages = someUsages([aUsage({ usage_id: 'a' }), aUsage({ usage_id: 'b' })], 3);

      // Act
      const count = usageCount(usages);

      // Assert
      expect(count).toBe(5);
    });

    it('given no hidden count should count the shown usages', () => {
      // Arrange
      const usages = { payload_usages: [aUsage()] };

      // Act
      const count = usageCount(usages);

      // Assert
      expect(count).toBe(1);
    });
  });

  describe('scenarioCount', () => {
    it('given usages in two scenarios and one outside should count two scenarios', () => {
      // Arrange
      const usages = someUsages([
        aUsage({
          usage_id: 'a',
          usage_scenario_id: 's1',
        }),
        aUsage({
          usage_id: 'b',
          usage_scenario_id: 's1',
        }),
        aUsage({
          usage_id: 'c',
          usage_scenario_id: 's2',
        }),
        aUsage({
          usage_id: 'd',
          usage_scenario_id: undefined,
          usage_type: 'atomic_testing',
        }),
      ]);

      // Act
      const count = scenarioCount(usages);

      // Assert
      expect(count).toBe(2);
    });
  });

  describe('olderVersionsInUse', () => {
    it('given versions with and without usages should keep the other versions in use', () => {
      // Arrange
      const latest = aVersion({
        payload_id: 'payload-v3',
        payload_action_id: 'action-v3',
      });
      const used = aVersion({
        payload_id: 'payload-v2',
        payload_action_id: 'action-v2',
      });
      const unused = aVersion();
      const usages = {
        'payload-v3': someUsages([aUsage()]),
        'payload-v2': someUsages([], 2),
        'payload-v1': someUsages([]),
      };

      // Act
      const older = olderVersionsInUse([latest, used, unused], usages, 'action-v3');

      // Assert
      expect(older).toEqual([{
        version: used,
        usages: usages['payload-v2'],
      }]);
    });
  });
});
