import type { AssetGroupSimple, EndpointSimple, ExerciseSimple, RelatedFindingOutput, ScenarioSimple } from '../../../utils/api-types';

// Anchor of the "Impacted assets" section: the hero counter links to it.
export const IMPACTED_ASSETS_ANCHOR = 'impacted-assets';

export interface FindingImpact {
  assets: EndpointSimple[];
  assetGroups: AssetGroupSimple[];
  scenarios: ScenarioSimple[];
  simulations: ExerciseSimple[];
}

const byName = <T>(name: (item: T) => string | undefined) => (a: T, b: T) => (name(a) ?? '').localeCompare(name(b) ?? '');

const distinct = <T>(items: (T | undefined | null)[], id: (item: T) => string | undefined): T[] => {
  const seen = new Map<string, T>();
  items.forEach((item) => {
    const key = item ? id(item) : undefined;
    if (item && key && !seen.has(key)) {
      seen.set(key, item);
    }
  });
  return [...seen.values()];
};

// A finding (type + value) is stored once per occurrence: its impact is the union, deduplicated by
// id and sorted by name, of what every occurrence touched (assets, asset groups) and where it ran
// (scenarios, simulations). An atomic testing occurrence has neither scenario nor simulation.
export const collectFindingImpact = (occurrences: RelatedFindingOutput[]): FindingImpact => ({
  assets: distinct(occurrences.flatMap(o => o.finding_assets ?? []), a => a.asset_id)
    .sort(byName(a => a.asset_name)),
  assetGroups: distinct(occurrences.flatMap(o => o.finding_asset_groups ?? []), g => g.asset_group_id)
    .sort(byName(g => g.asset_group_name)),
  scenarios: distinct(occurrences.map(o => o.finding_scenario), s => s.scenario_id)
    .sort(byName(s => s.scenario_name)),
  simulations: distinct(occurrences.map(o => o.finding_simulation), s => s.exercise_id)
    .sort(byName(s => s.exercise_name)),
});
