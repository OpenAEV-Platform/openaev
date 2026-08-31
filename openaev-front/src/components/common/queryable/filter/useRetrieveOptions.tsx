import type { AxiosResponse } from 'axios';
import { useState } from 'react';

import { searchAssetGroupByIdAsOption } from '../../../../actions/asset_groups/assetgroup-action';
import { searchAssetsByIdAsOption, searchEndpointByIdAsOption } from '../../../../actions/assets/endpoint-actions';
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
import { searchPlayerByIdAsOption } from '../../../../actions/users/User';
import ContractOutputElementType from '../../../../admin/components/findings/ContractOutputElementType';
import { scenarioCategories } from '../../../../admin/components/scenarios/constants';
import { type GroupOption, type Option } from '../../../../utils/Option';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { useFormatter } from '../../../i18n';
import { CUSTOM_DASHBOARD, SCENARIOS, SIMULATIONS } from './constants';

interface RetrieveOptionsConfig {
  defaultValues?: GroupOption[] | undefined;
  contextId?: string;
  filterKey: string;
}

const useRetrieveOptions = () => {
  const { t } = useFormatter();
  const [options, setOptions] = useState<Option[]>([]);
  const ability = useAbility();

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
      case 'action_injectors':
      case 'injector_contract_injectors':
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
      case 'action_domains':
      case 'injector_contract_domains':
      case 'inject_contract_domains':
      case 'base_security_domains_side':
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
      case 'injector_contract_tags':
      case 'inject_tags':
      case 'action_tags':
      case 'scenario_tags':
      case 'target_tags':
      case 'team_tags':
      case 'finding_tags':
      case 'user_tags':
      case 'document_tags':
      case 'challenge_tags':
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
        // Findings can attach to any asset category (agentless web applications included), so
        // labels must resolve through the whole asset inventory, not the endpoint-only options.
        if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
          searchAssetsByIdAsOption(ids).then((response) => {
            setOptions(response.data);
          });
        } else {
          setOptions([]);
        }
        break;
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
      case 'finding_teams':
      case 'user_teams':
      case 'base_teams_side':
        searchTeamByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_users':
      case 'payload_author_user':
      case 'asset_linked_person':
        searchPlayerByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_author_team':
        searchTeamByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'payload_author_organization':
        searchOrganizationByIdAsOptions(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_inject_id':
        searchInjectByIdAsOption(ids).then((response) => {
          setOptions(response.data);
        });
        break;
      case 'finding_type':
        setOptions(ids.map(id => ({
          id,
          label: ContractOutputElementType[id as keyof typeof ContractOutputElementType] ?? id,
        })));
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
      case 'scenario_category':
        // Predefined categories have a display label; custom ones show as-is.
        setOptions(ids.map(id => ({
          id,
          label: scenarioCategories.has(id) ? t(scenarioCategories.get(id) as string) : id,
        })));
        break;
      // Hero-stat drill-downs scope entities without ES side fields (teams,
      // asset groups) by their explicit ids: resolve the labels across both
      // types and merge (each id only matches its own type).
      case 'base_id':
        Promise.all([
          searchTeamByIdAsOption(ids),
          ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)
            ? searchAssetGroupByIdAsOption(ids)
            : Promise.resolve({ data: [] as Option[] }),
        ]).then(([teams, assetGroups]) => {
          setOptions([...teams.data, ...assetGroups.data]);
        });
        break;
      // Author filter: an id may belong to a person, a team or an organization -
      // resolve across all three and merge (each id only matches its own type).
      case 'action_author':
        Promise.all([
          searchPlayerByIdAsOption(ids),
          searchTeamByIdAsOption(ids),
          searchOrganizationByIdAsOptions(ids),
        ]).then(([players, teams, organizations]) => {
          setOptions([...players.data, ...teams.data, ...organizations.data]);
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
