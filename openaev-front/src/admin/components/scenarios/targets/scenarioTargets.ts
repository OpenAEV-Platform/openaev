import { type InjectTargetsOutput, type ScenarioTargetsInput } from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';

export interface ScenarioTargets {
  assets: Option[];
  assetGroups: Option[];
  teams: Option[];
  manual: string;
}

export type TargetKind = InjectTargetsOutput['inject_target_kinds'][number];

export const NO_TARGETS: ScenarioTargets = {
  assets: [],
  assetGroups: [],
  teams: [],
  manual: '',
};

const manualTargets = (manual: string) => manual.split(/[\s,;]+/).filter(target => target.length > 0);

const IS_CHOSEN: Record<TargetKind, (targets: ScenarioTargets) => boolean> = {
  ASSETS: targets => targets.assets.length > 0,
  ASSET_GROUPS: targets => targets.assetGroups.length > 0,
  TEAMS: targets => targets.teams.length > 0,
  HOSTS: targets => manualTargets(targets.manual).length > 0,
};

// As the server adds them: an inject gets a target when one of the kinds it takes is chosen.
export const isTargetedWith = (inject: InjectTargetsOutput, targets: ScenarioTargets) => inject.inject_targeted
  || inject.inject_target_kinds.some(kind => IS_CHOSEN[kind](targets));

export const toTargetsInput = (targets: ScenarioTargets): ScenarioTargetsInput => ({
  asset_ids: targets.assets.map(option => option.id),
  asset_group_ids: targets.assetGroups.map(option => option.id),
  team_ids: targets.teams.map(option => option.id),
  manual_targets: manualTargets(targets.manual),
});
