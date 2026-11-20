import { describe, expect, it } from 'vitest';

import buildScenarioTabs from '../../../../../admin/components/scenarios/scenario/scenarioTabs';

const t = (value: string) => value;

describe('buildScenarioTabs', () => {
  describe.each([true, false])('given a scenario chained: %s', (isChained) => {
    it('given_catalogScenario_should_endWithTheHistoryTab', () => {
      // Arrange & Act
      const tabs = buildScenarioTabs({
        isChained,
        lessonsEnabled: false,
        fromCatalog: true,
        t,
      });

      // Assert
      expect(tabs.at(-1)).toEqual(['/history', 'History']);
    });

    it('given_scenarioMadeInTheTenant_should_haveNoHistoryTab', () => {
      // Arrange & Act
      const tabs = buildScenarioTabs({
        isChained,
        lessonsEnabled: false,
        fromCatalog: false,
        t,
      });

      // Assert
      expect(tabs.map(([suffix]) => suffix)).not.toContain('/history');
    });
  });
});
