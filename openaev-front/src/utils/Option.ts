import * as R from 'ramda';

import countriesJson from '../static/geo/countries.json';
import { type AttackPattern, type Exercise, type KillChainPhase, type Organization, type Scenario, type Tag } from './api-types';

interface Countries {
  features: [{
    properties: {
      ISO3: string;
      NAME: string;
    };
  }];
}

//  eslint-disable-next-line @typescript-eslint/no-explicit-any
const countries: Countries = countriesJson as any;

export interface Option {
  id: string;
  label: string;
  color?: string;
}

export interface GroupOption extends Option { group: string }

export const createGroupOption: (id: string, label: string, group: string, color?: string) => GroupOption = (id, label, group, color?) => {
  return {
    id: id,
    label: label,
    group: group,
    color: color,
  };
};

export const tagOptions = (
  tag_ids: string[] | undefined,
  tagsMap: Record<string, Tag>,
) => (tag_ids ?? [])
  .map(tagId => tagsMap[tagId])
  .filter(tagItem => tagItem !== undefined)
  .map(
    tagItem => ({
      id: tagItem.tag_id,
      label: tagItem.tag_name,
      color: tagItem.tag_color,
    }) as Option,
  );

export const attackPatternOptions = (
  attack_pattern_ids: string[] | undefined,
  attackPatternsMap: Record<string, AttackPattern>,
  killChainPhasesMap: Record<string, KillChainPhase>,
) => (attack_pattern_ids ?? [])
  .map(attackPatternId => attackPatternsMap[attackPatternId])
  .filter(attackPatternItem => attackPatternItem !== undefined)
  .map(
    (attackPatternItem) => {
      const killChainPhase = R.head(attackPatternItem.attack_pattern_kill_chain_phases);
      const killChainName = killChainPhase ? killChainPhasesMap[killChainPhase]?.phase_kill_chain_name ?? null : null;
      return {
        id: attackPatternItem.attack_pattern_id,
        label: killChainName ? `[${killChainName}] [${attackPatternItem.attack_pattern_external_id}] ${attackPatternItem.attack_pattern_name}` : `[${attackPatternItem.attack_pattern_external_id}] ${attackPatternItem.attack_pattern_name}`,
      } as Option;
    },
  );

export const killChainPhaseOptions = (
  kill_chain_phase_ids: string[] | undefined,
  killChainPhasesMap: Record<string, KillChainPhase>,
) => (kill_chain_phase_ids ?? [])
  .map(killChainPhaseId => killChainPhasesMap[killChainPhaseId])
  .filter(killChainPhaseItem => killChainPhaseItem !== undefined)
  .map(
    killChainPhaseItem => ({
      id: killChainPhaseItem.phase_id,
      label: `[${killChainPhaseItem.phase_kill_chain_name}] ${killChainPhaseItem.phase_name}`,
    }) as Option,
  );

export const exerciseOptions = (
  exercise_ids: string[],
  exercisesMap: Record<string, Exercise>,
) => (exercise_ids ?? [])
  .map(exerciseId => exercisesMap[exerciseId])
  .filter(exerciseItem => exerciseItem !== undefined)
  .map(
    exerciseItem => ({
      id: exerciseItem.exercise_id,
      label: exerciseItem.exercise_name,
    }) as Option,
  );

export const scenarioOptions = (
  scenario_ids: string[],
  scenariosMap: Record<string, Scenario>,
) => (scenario_ids ?? [])
  .map(scenarioId => scenariosMap[scenarioId])
  .filter(scenarioItem => scenarioItem !== undefined)
  .map(
    scenarioItem => ({
      id: scenarioItem.scenario_id,
      label: scenarioItem.scenario_name,
    }) as Option,
  );

export const organizationOption = (
  organizationId: string | undefined,
  organizationsMap: Record<string, Organization>,
) => {
  if (!organizationId) {
    return undefined;
  }
  const value = organizationsMap[organizationId];
  return value
    ? ({
        id: value.organization_id,
        label: value.organization_name,
      } as Option)
    : undefined;
};

export const countryOptions = () => countries.features.map(
  n => ({
    id: n.properties.ISO3,
    label: n.properties.NAME,
  }) as Option,
);

export const countryOption = (iso3: string | undefined) => {
  if (!iso3) {
    return undefined;
  }
  const country = R.head(
    countries.features.filter(n => n.properties.ISO3 === iso3),
  );
  return {
    id: country.properties.ISO3,
    label: country.properties.NAME,
  } as Option;
};
