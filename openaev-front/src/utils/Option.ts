import countriesJson from '../static/geo/countries.json';
import { type AttackPattern, type Exercise, type KillChainPhase, type Organization, type Scenario, type Tag } from './api-types';

interface Country {
  code: string;
  name: string;
}
type Countries = Country[];

export interface Option {
  id: string;
  label: string;
  color?: string;
}

export interface GroupOption extends Option { group: string }

const countries = countriesJson as Countries;

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
      const killChainPhase = attackPatternItem.attack_pattern_kill_chain_phases?.[0];
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

export const countryOptions = () => countries.map(
  n => ({
    id: n.code,
    label: n.name,
  }) as Option,
);

export const countryOption = (iso3: string | undefined) => {
  if (!iso3) {
    return undefined;
  }
  const country = countries.find(n => n.code === iso3);
  if (!country) {
    return undefined;
  }
  return {
    id: country.code,
    label: country.name,
  } as Option;
};
