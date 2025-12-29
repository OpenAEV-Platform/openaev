import type { AxiosResponse } from 'axios';
import { useContext, useState } from 'react';

import { searchAssetGroupByIdAsOption } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpointByIdAsOption } from '../../../../actions/assets/endpoint-actions';
import { searchSecurityPlatformByIdAsOption } from '../../../../actions/assets/securityPlatform-actions';
import { searchAttackPatternsByIdAsOption } from '../../../../actions/AttackPattern';
import { searchCustomDashboardByIdAsOptions } from '../../../../actions/custom_dashboards/customdashboard-action';
import { searchDomainsByIdsAsOption } from '../../../../actions/domains/domain-actions';
import { searchExerciseByIdAsOption } from '../../../../actions/exercises/exercise-action';
import { searchInjectorByIdAsOptions } from '../../../../actions/injectors/injector-action';
import { searchInjectByIdAsOption, searchTargetOptionsById } from '../../../../actions/injects/inject-action';
import { searchKillChainPhasesByIdAsOption } from '../../../../actions/kill_chain_phases/killChainPhase-action';
import { searchOrganizationByIdAsOptions } from '../../../../actions/organizations/organization-actions';
import { searchScenarioByIdAsOption } from '../../../../actions/scenarios/scenario-actions';
import { searchSimulationByIdAsOptions } from '../../../../actions/simulations/simulation-action';
import { searchTagByIdAsOption } from '../../../../actions/tags/tag-action';
import { searchTeamByIdAsOption } from '../../../../actions/teams/team-actions';
import { type GroupOption, type Option } from '../../../../utils/Option';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { CUSTOM_DASHBOARD, SCENARIOS, SIMULATIONS } from './constants';

interface RetrieveOptionsConfig {
  defaultValues?: GroupOption[] | undefined;
  contextId?: string;
  filterKey: string;
}

const useRetrieveOptions = () => {
  const [options, setOptions] = useState<Option[]>([]);
  const ability = useContext(AbilityContext);

  const handleOptions = (response: AxiosResponse<GroupOption[] | Option[]>, filterDefaultValues: GroupOption[]) => {
    if (filterDefaultValues && filterDefaultValues.length > 0) {
      setOptions([...filterDefaultValues, ...response.data.map((d: Option) => ({
        ...d,
        group: 'Values',
      }))]);
    } else {
      setOptions(response.data);
    }
  };

  const searchOptions = (ids: string[], config: RetrieveOptionsConfig) => {
    const { filterKey, contextId = '' } = config;
    const filterDefaultValues = (config.defaultValues ?? []).filter(v => ids.includes(v.id));
    switch (filterKey) {
      case SIMULATIONS:
      case 'base_simulation_side':
        searchSimulationByIdAsOptions(ids).then((response) => {
          handleOptions(response, filterDefaultValues);
        });
        break;
      case 'injector_contract_injector':
      case 'inject_injector_contract':
        searchInjectorByIdAsOptions(ids, contextId).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'injector_contract_kill_chain_phases':
      case 'scenario_kill_chain_phases':
      case 'exercise_kill_chain_phases':
      case 'inject_kill_chain_phases':
        searchKillChainPhasesByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_attack_patterns':
      case 'base_attack_patterns_side':
      case 'inject_attack_patterns':
        searchAttackPatternsByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_domains':
      case 'injector_contract_domains':
      case 'inject_contract_domains':
        searchDomainsByIdsAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'target_asset_groups':
        // TODO allow to fetch for a specific resource if no capa issue/3864
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchTargetOptionsById('ASSETS_GROUPS', ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'target_assets':
      case 'target_endpoint':
      case 'base_endpoint_side':
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchTargetOptionsById('ASSETS', ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'target_teams':
        searchTargetOptionsById('TEAMS', ids).then((response) => {
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
        searchTagByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_asset_groups':
      case 'inject_asset_groups':
      case 'base_asset_groups_side':
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchAssetGroupByIdAsOption(ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'finding_assets':
      case 'inject_assets':
      case 'base_assets_side':
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchEndpointByIdAsOption(ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'inject_teams':
      case 'base_teams_side':
        searchTeamByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_inject_id':
        searchInjectByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_simulation':
        searchExerciseByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_scenario' :
      case 'exercise_scenario':
      case 'base_scenario_side':
      case SCENARIOS:
        searchScenarioByIdAsOption(ids).then((response) => {
          handleOptions(response, filterDefaultValues);
        });
        break;
      case 'user_organization':
        searchOrganizationByIdAsOptions(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case CUSTOM_DASHBOARD:
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.DASHBOARDS)) {
          searchCustomDashboardByIdAsOptions(ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
      case 'base_security_platforms_side':
        searchSecurityPlatformByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      default:
        setOptions(ids.map(id => ({
          id,
          label: id,
        })));
        break;
    }
  };

  return {
    options,
    searchOptions,
  };
};

export default useRetrieveOptions;
