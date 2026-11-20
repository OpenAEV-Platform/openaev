import { type LaunchTargetsInput } from '../../../../actions/scenarios/scenario-actions';
import { type Option } from '../../../../utils/Option';

export interface ScenarioTargets {
  assets: Option[];
  assetGroups: Option[];
  teams: Option[];
  manual: string;
}

export const NO_TARGETS: ScenarioTargets = {
  assets: [],
  assetGroups: [],
  teams: [],
  manual: '',
};

const manualTargets = (manual: string) => manual.split(/[\s,;]+/).filter(target => target.length > 0);

export const toTargetsInput = (targets: ScenarioTargets): LaunchTargetsInput => ({
  asset_ids: targets.assets.map(option => option.id),
  asset_group_ids: targets.assetGroups.map(option => option.id),
  team_ids: targets.teams.map(option => option.id),
  manual_targets: manualTargets(targets.manual),
});
