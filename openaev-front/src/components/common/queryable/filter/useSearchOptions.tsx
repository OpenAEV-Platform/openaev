import { type AxiosResponse } from 'axios';
import { useState } from 'react';

import { searchAssetGroupAsOption, searchAssetGroupLinkedToFindingsAsOption } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpointAsOption, searchEndpointLinkedToFindingsAsOption } from '../../../../actions/assets/endpoint-actions';
import { searchSecurityPlatformAsOption } from '../../../../actions/assets/securityPlatform-actions';
import { searchAttackPatternsByNameAsOption } from '../../../../actions/AttackPattern';
import { searchCustomDashboardAsOptions } from '../../../../actions/custom_dashboards/customdashboard-action';
import { searchDomainsByNameAsOption } from '../../../../actions/domains/domain-actions';
import { searchExerciseLinkedToFindingsAsOption } from '../../../../actions/exercises/exercise-action';
import { searchInjectorsByNameAsOption } from '../../../../actions/injectors/injector-action';
import { searchInjectLinkedToFindingsAsOption, searchTargetOptions } from '../../../../actions/injects/inject-action';
import { searchKillChainPhasesByNameAsOption } from '../../../../actions/kill_chain_phases/killChainPhase-action';
import { searchOrganizationsByNameAsOption } from '../../../../actions/organizations/organization-actions';
import { searchScenarioAsOption, searchScenarioCategoryAsOption } from '../../../../actions/scenarios/scenario-actions';
import { searchScenarioSimulationsAsOption } from '../../../../actions/scenarios/scenario-simulation-action';
import { searchSimulationAsOptions } from '../../../../actions/simulations/simulation-action';
import { searchTagAsOption } from '../../../../actions/tags/tag-action';
import { searchTeamsAsOption } from '../../../../actions/teams/team-actions';
import { type GroupOption, type Option } from '../../../../utils/Option';
import { useFormatter } from '../../../i18n';
import { CUSTOM_DASHBOARD, SCENARIO_SIMULATIONS, SCENARIOS, SIMULATIONS } from './constants';

export interface SearchOptionsConfig {
  filterKey: string;
  contextId?: string;
  defaultValues?: GroupOption[] | undefined;
}

const useSearchOptions = () => {
  // Standard hooks
  const { t } = useFormatter();

  const [options, setOptions] = useState<GroupOption[] | Option[]>([]);

  const handleOptions = (response: AxiosResponse<GroupOption[] | Option[]>, defaultValues: GroupOption[] | undefined) => {
    if (defaultValues && defaultValues.length > 0) {
      setOptions([...defaultValues, ...response.data.map((d: Option) => ({
        ...d,
        group: 'Values',
      }))]);
    } else {
      setOptions(response.data);
    }
  };

  const searchOptions = (config: SearchOptionsConfig, search: string = '') => {
    const { filterKey, contextId = '' } = config;
    switch (filterKey) {
      case SIMULATIONS:
      case 'base_simulation_side':
        searchSimulationAsOptions(search).then((response) => {
          handleOptions(response, config.defaultValues);
        });
        break;
      case 'injector_contract_injector':
      case 'inject_injector_contract':
        searchInjectorsByNameAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'injector_contract_kill_chain_phases':
      case 'scenario_kill_chain_phases':
      case 'exercise_kill_chain_phases':
      case 'inject_kill_chain_phases':
        searchKillChainPhasesByNameAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_attack_patterns':
      case 'base_attack_patterns_side':
      case 'inject_attack_patterns':
        searchAttackPatternsByNameAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_domains':
      case 'injector_contract_domains':
      case 'inject_contract_domains':
        searchDomainsByNameAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'target_asset_groups':
        searchTargetOptions(contextId, 'ASSETS_GROUPS', search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'target_assets':
      case 'target_endpoint':
        searchTargetOptions(contextId, 'ASSETS', search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'target_teams':
        searchTargetOptions(contextId, 'TEAMS').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'asset_tags':
      case 'asset_group_tags':
      case 'exercise_tags':
      case 'inject_tags':
      case 'payload_tags':
      case 'scenario_tags':
      case 'target_tags':
      case 'team_tags':
      case 'finding_tags':
      case 'user_tags':
      case 'base_tags_side':
        searchTagAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_asset_groups':
        searchAssetGroupLinkedToFindingsAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'inject_asset_groups':
        searchAssetGroupAsOption(search, contextId, contextId ? 'SIMULATION_OR_SCENARIO' : 'ATOMIC_TESTING').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'base_asset_groups_side':
        searchAssetGroupAsOption(search, contextId, 'ALL_INJECTS').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_assets':
        searchEndpointLinkedToFindingsAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'inject_assets':
      case 'base_endpoint_side':
        searchEndpointAsOption(search, contextId, contextId ? 'SIMULATION_OR_SCENARIO' : 'ATOMIC_TESTING').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'base_assets_side':
        searchEndpointAsOption(search, contextId, 'ALL_INJECTS').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'inject_teams':
        searchTeamsAsOption(search, contextId, contextId ? 'SIMULATION_OR_SCENARIO' : 'ATOMIC_TESTING').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'base_teams_side':
        searchTeamsAsOption(search, contextId, 'ALL_INJECTS').then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_inject_id':
        searchInjectLinkedToFindingsAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_simulation':
        searchExerciseLinkedToFindingsAsOption(search, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_scenario':
      case 'exercise_scenario':
      case 'base_scenario_side':
      case SCENARIOS:
        searchScenarioAsOption(search).then((response) => {
          handleOptions(response, config.defaultValues);
        });
        break;
      case 'scenario_category':
        searchScenarioCategoryAsOption(search).then((response: { data: Option[] }) => {
          setOptions(response.data.map(d => ({
            id: d.id,
            label: t(d.label),
          })));
        });
        break;
      case 'user_organization':
        searchOrganizationsByNameAsOption(search).then((response: { data: Option[] }) => {
          setOptions(response.data.map(d => ({
            id: d.id,
            label: t(d.label),
          })));
        });
        break;
      case CUSTOM_DASHBOARD:
        searchCustomDashboardAsOptions(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'base_security_platforms_side':
        searchSecurityPlatformAsOption(search).then((response) => {
          setOptions(response.data);
        });
        break;
      case SCENARIO_SIMULATIONS:
        searchScenarioSimulationsAsOption(contextId, search).then((response) => {
          setOptions(response.data);
        });
        break;
      default:
    }
  };

  return {
    options,
    setOptions,
    searchOptions,
  };
};

export default useSearchOptions;
