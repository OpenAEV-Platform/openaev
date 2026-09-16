import { describe, expect, it } from 'vitest';

import type { InjectTarget } from '../../../utils/api-types';
import { getTargetOverviewLabel, getTargetOverviewUrl } from '../../../utils/target/TargetUtils';

const TARGET_ID = '123e4567-e89b-12d3-a456-426614174000';

const buildTarget = (targetType: string): InjectTarget => ({
  target_id: TARGET_ID,
  target_type: targetType,
  target_name: 'target',
} as InjectTarget);

describe('TargetUtils', () => {
  // -- getTargetOverviewUrl --

  describe('getTargetOverviewUrl', () => {
    it.each([
      ['ASSETS', `/admin/assets/${TARGET_ID}`],
      ['AI_TARGETS', `/admin/assets/${TARGET_ID}`],
      ['ASSETS_GROUPS', `/admin/asset_groups/${TARGET_ID}`],
      ['TEAMS', `/admin/teams/${TARGET_ID}`],
      ['PLAYERS', `/admin/persons/${TARGET_ID}`],
    ])('given %s target should return its overview url', (targetType, expected) => {
      // Act
      const result = getTargetOverviewUrl(buildTarget(targetType));

      // Assert
      expect(result).toBe(expected);
    });

    it('given AGENT target should return null', () => {
      // Agents have no standalone overview page: their endpoint id is not
      // serialized to the client, so no pivot can be built.
      // Act
      const result = getTargetOverviewUrl(buildTarget('AGENT'));

      // Assert
      expect(result).toBeNull();
    });
  });

  // -- getTargetOverviewLabel --

  describe('getTargetOverviewLabel', () => {
    it.each([
      ['ASSETS', 'Open asset overview'],
      ['AI_TARGETS', 'Open asset overview'],
      ['ASSETS_GROUPS', 'Open asset group overview'],
      ['TEAMS', 'Open team overview'],
      ['PLAYERS', 'Open person overview'],
    ])('given %s target should return its overview label key', (targetType, expected) => {
      // Act
      const result = getTargetOverviewLabel(buildTarget(targetType));

      // Assert
      expect(result).toBe(expected);
    });
  });
});
