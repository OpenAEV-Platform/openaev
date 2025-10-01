import { type AssetGroup, type Exercise, type Scenario } from '../../utils/api-types';

export interface AssetGroupsHelper {
  getAssetGroups: () => AssetGroup[];
  getAssetGroupMaps: () => Record<string, AssetGroup>;
  getAssetGroup: (assetGroupId: string) => AssetGroup | undefined;
  getExerciseAssetGroups: (exerciseId: Exercise['exercise_id']) => AssetGroup[];
  getScenarioAssetGroups: (scenarioId: Scenario['scenario_id']) => AssetGroup[];
}
