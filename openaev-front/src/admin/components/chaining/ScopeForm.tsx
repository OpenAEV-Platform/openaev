import { GroupsOutlined, PersonOutlined } from '@mui/icons-material';
import { Box, Button, Tab, Tabs, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import {
  type FunctionComponent,
  type SyntheticEvent,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

import { findAssetGroups, searchAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { findEndpoints, searchEndpoints } from '../../../actions/assets/endpoint-actions';
import { fetchExecutors } from '../../../actions/executors/executor-action';
import type { ExecutorHelper } from '../../../actions/executors/executor-helper';
import { searchPlayers } from '../../../actions/players/player-actions';
import { searchTeamByIdAsOption, searchTeams } from '../../../actions/teams/team-actions';
import { searchPlayerByIdAsOption } from '../../../actions/users/User';
import ClickableList, { type ClickableListElements } from '../../../components/common/ClickableList';
import { SectionLabel } from '../../../components/common/detail/EntityDetailCommon';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import type { AssetGroupOutput, EndpointOutput, PlayerOutput, TeamOutput } from '../../../utils/api-types';
import { getActiveMsgTooltip, getExecutorsCount } from '../../../utils/endpoints/utils';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../utils/url-helper';
import { download } from '../../../utils/utils';
import AssetCategoryIcon from '../assets/AssetCategoryIcon';
import AssetStatus from '../assets/AssetStatus';
import {
  buildScopeRulesCsvTemplate,
  parseScopeRulesCsv,
} from './scope-rules-csv';
import ScopeInventoryBox from './ScopeInventoryBox';

export interface ScopeCustomRule {
  source: 'MANUAL' | 'CSV';
  value: string;
}

interface ScopeIdOption {
  id: string;
  label: string;
}

interface ScopeFormProps {
  mode: 'ALLOWLIST' | 'DENYLIST';
  selectedEndpointIds: string[];
  selectedAssetGroupIds: string[];
  selectedTeamIds: string[];
  selectedPlayerIds: string[];
  selectedCustomRules: ScopeCustomRule[];
  initialEndpointIds: string[];
  initialAssetGroupIds: string[];
  initialTeamIds: string[];
  initialPlayerIds: string[];
  initialCustomRules: ScopeCustomRule[];
  onEndpointIdsChange: (ids: string[]) => void;
  onAssetGroupIdsChange: (ids: string[]) => void;
  onTeamIdsChange: (ids: string[]) => void;
  onPlayerIdsChange: (ids: string[]) => void;
  onCustomRulesChange: (rules: ScopeCustomRule[]) => void;
  onCancel: () => void;
  onSubmit: () => void;
}

const ScopeForm: FunctionComponent<ScopeFormProps> = ({
  mode,
  selectedEndpointIds,
  selectedAssetGroupIds,
  selectedTeamIds,
  selectedPlayerIds,
  selectedCustomRules,
  initialEndpointIds,
  initialAssetGroupIds,
  initialTeamIds,
  initialPlayerIds,
  initialCustomRules,
  onEndpointIdsChange,
  onAssetGroupIdsChange,
  onTeamIdsChange,
  onPlayerIdsChange,
  onCustomRulesChange,
  onCancel,
  onSubmit,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  // Tab state
  const [currentTab, setCurrentTab] = useState<string>('assets');

  const listLabel = mode === 'ALLOWLIST' ? t('Allowlisted') : t('Denylisted');
  const addLabel = mode === 'ALLOWLIST' ? t('Add assets, asset groups, teams and persons to your allowlist') : t('Add assets, asset groups, teams and persons to your denylist');

  // -- Selected values (inventory) --
  const [selectedEndpoints, setSelectedEndpoints] = useState<EndpointOutput[]>([]);
  const [selectedAssetGroups, setSelectedAssetGroups] = useState<AssetGroupOutput[]>([]);
  const [selectedTeamOptions, setSelectedTeamOptions] = useState<ScopeIdOption[]>([]);
  const [selectedPlayerOptions, setSelectedPlayerOptions] = useState<ScopeIdOption[]>([]);

  const { executorsMap } = useHelper((helper: ExecutorHelper) => ({ executorsMap: helper.getExecutorsMap() }));

  useDataLoader(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
      dispatch(fetchExecutors());
    }
  });

  useEffect(() => {
    if (selectedEndpointIds.length > 0) {
      findEndpoints(selectedEndpointIds).then(result => setSelectedEndpoints(result.data));
    } else {
      setSelectedEndpoints([]);
    }
  }, [selectedEndpointIds]);

  useEffect(() => {
    if (selectedAssetGroupIds.length > 0) {
      findAssetGroups(selectedAssetGroupIds).then(result => setSelectedAssetGroups(result.data));
    } else {
      setSelectedAssetGroups([]);
    }
  }, [selectedAssetGroupIds]);

  useEffect(() => {
    if (selectedTeamIds.length > 0) {
      searchTeamByIdAsOption(selectedTeamIds).then(result => setSelectedTeamOptions(result.data as ScopeIdOption[]));
    } else {
      setSelectedTeamOptions([]);
    }
  }, [selectedTeamIds]);

  useEffect(() => {
    if (selectedPlayerIds.length > 0) {
      searchPlayerByIdAsOption(selectedPlayerIds).then(result => setSelectedPlayerOptions(result.data as ScopeIdOption[]));
    } else {
      setSelectedPlayerOptions([]);
    }
  }, [selectedPlayerIds]);

  const totalSelected = selectedEndpointIds.length + selectedAssetGroupIds.length
    + selectedTeamIds.length + selectedPlayerIds.length + selectedCustomRules.length;

  const hasChanges = useMemo(() => {
    const sortedCurrent = [
      ...selectedEndpointIds,
      ...selectedAssetGroupIds,
      ...selectedTeamIds.map(id => `team:${id}`),
      ...selectedPlayerIds.map(id => `player:${id}`),
      ...selectedCustomRules.map(r => `${r.source}:${r.value.toLowerCase()}`),
    ].sort((a, b) => a.localeCompare(b));
    const sortedInitial = [
      ...initialEndpointIds,
      ...initialAssetGroupIds,
      ...initialTeamIds.map(id => `team:${id}`),
      ...initialPlayerIds.map(id => `player:${id}`),
      ...initialCustomRules.map(r => `${r.source}:${r.value.toLowerCase()}`),
    ].sort((a, b) => a.localeCompare(b));
    if (sortedCurrent.length !== sortedInitial.length) return true;
    return sortedCurrent.some((id, i) => id !== sortedInitial[i]);
  }, [
    selectedEndpointIds,
    selectedAssetGroupIds,
    selectedTeamIds,
    selectedPlayerIds,
    selectedCustomRules,
    initialEndpointIds,
    initialAssetGroupIds,
    initialTeamIds,
    initialPlayerIds,
    initialCustomRules,
  ]);

  // -- Assets tab (endpoints) --
  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  const [isLoadingEndpoints, setIsLoadingEndpoints] = useState(false);

  const { queryableHelpers: endpointQueryableHelpers, searchPaginationInput: endpointSearchPagination }
    = useQueryable(buildSearchPagination({}));

  const endpointElements: ClickableListElements<EndpointOutput> = useMemo(() => ({
    // Category-aware glyph (same as the assets inventory page) so non-host assets
    // (web applications, cloud resources, ...) don't show the generic device icon.
    icon: { value: (endpoint: EndpointOutput) => <AssetCategoryIcon category={endpoint.asset_category} color="primary" /> },
    headers: [
      {
        field: 'asset_name',
        value: (endpoint: EndpointOutput) => endpoint.asset_name,
        width: 35,
      },
      {
        field: 'endpoint_active',
        value: (endpoint: EndpointOutput) => {
          const status = getActiveMsgTooltip(endpoint.asset_agents.map(a => a.agent_active ?? false), t('Active'), t('Inactive'), t('Agentless'));
          return (
            <Tooltip title={status.activeMsgTooltip}>
              <span>
                <AssetStatus variant="list" status={status.status} />
              </span>
            </Tooltip>
          );
        },
        width: 20,
      },
      {
        field: 'endpoint_platform',
        value: (endpoint: EndpointOutput) => (
          <div style={{
            display: 'flex',
            alignItems: 'center',
          }}
          >
            <PlatformIcon platform={endpoint.endpoint_platform} width={20} marginRight={theme.spacing(2)} />
          </div>
        ),
        width: 10,
      },
      {
        field: 'endpoint_agents_executor',
        value: (endpoint: EndpointOutput) => {
          if (endpoint.asset_agents.length > 0) {
            const groupedExecutors = getExecutorsCount(endpoint, executorsMap);
            return (
              <>
                {Object.keys(groupedExecutors).map((executorType) => {
                  const executorsOfType = groupedExecutors[executorType];
                  const count = executorsOfType.length;
                  const base = executorsOfType[0];
                  if (count > 0) {
                    return (
                      <Tooltip
                        key={executorType}
                        title={`${base.executor_name} : ${count}`}
                        arrow
                      >
                        <div style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                        }}
                        >
                          <img
                            src={buildTenantApiPath(`/api/images/executors/icons/${executorType}`)}
                            alt={executorType}
                            style={{
                              width: 20,
                              height: 20,
                              borderRadius: theme.borderRadius,
                              marginRight: theme.spacing(2),
                            }}
                          />
                        </div>
                      </Tooltip>
                    );
                  }
                  return t('Unknown');
                })}
              </>
            );
          }
          return <span>-</span>;
        },
        width: 15,
      },
      {
        field: 'asset_tags',
        value: (endpoint: EndpointOutput) => <ItemTags variant="reduced-view" tags={endpoint.asset_tags} />,
        width: 20,
      },
    ],
  }), [executorsMap]);

  const addEndpoint = (_id: string, endpoint: EndpointOutput) => {
    onEndpointIdsChange([...selectedEndpointIds, endpoint.asset_id]);
  };
  const removeEndpoint = (id: string) => {
    onEndpointIdsChange(selectedEndpointIds.filter(eid => eid !== id));
  };

  const endpointPagination = (
    <PaginationComponentV2
      fetch={searchEndpoints}
      searchPaginationInput={endpointSearchPagination}
      setContent={setEndpoints}
      setLoading={setIsLoadingEndpoints}
      entityPrefix="endpoint"
      availableFilterNames={['asset_tags', 'endpoint_platform', 'endpoint_arch']}
      queryableHelpers={endpointQueryableHelpers}
    />
  );

  // -- Asset groups tab --
  const [assetGroups, setAssetGroups] = useState<AssetGroupOutput[]>([]);
  const [isLoadingAssetGroups, setIsLoadingAssetGroups] = useState(false);

  const { queryableHelpers: assetGroupQueryableHelpers, searchPaginationInput: assetGroupSearchPagination }
    = useQueryable(buildSearchPagination({}));

  const assetGroupElements: ClickableListElements<AssetGroupOutput> = useMemo(() => ({
    icon: { value: () => <SelectGroup color="primary" /> },
    headers: [
      {
        field: 'asset_group_name',
        value: (ag: AssetGroupOutput) => <>{ag.asset_group_name}</>,
        width: 100,
      },
    ],
  }), []);

  const addAssetGroup = (_id: string, ag: AssetGroupOutput) => {
    onAssetGroupIdsChange([...selectedAssetGroupIds, ag.asset_group_id]);
  };
  const removeAssetGroup = (id: string) => {
    onAssetGroupIdsChange(selectedAssetGroupIds.filter(agId => agId !== id));
  };

  const assetGroupPagination = (
    <PaginationComponentV2
      fetch={searchAssetGroups}
      searchPaginationInput={assetGroupSearchPagination}
      setContent={setAssetGroups}
      setLoading={setIsLoadingAssetGroups}
      entityPrefix="asset_group"
      availableFilterNames={['asset_group_tags']}
      queryableHelpers={assetGroupQueryableHelpers}
    />
  );

  // -- Teams tab --
  const [teams, setTeams] = useState<TeamOutput[]>([]);
  const [isLoadingTeams, setIsLoadingTeams] = useState(false);

  const { queryableHelpers: teamQueryableHelpers, searchPaginationInput: teamSearchPagination }
    = useQueryable(buildSearchPagination({}));

  const teamElements: ClickableListElements<TeamOutput> = useMemo(() => ({
    icon: { value: () => <GroupsOutlined color="primary" /> },
    headers: [
      {
        field: 'team_name',
        value: (team: TeamOutput) => <>{team.team_name}</>,
        width: 100,
      },
    ],
  }), []);

  const addTeamSelection = (_id: string, team: TeamOutput) => {
    onTeamIdsChange([...selectedTeamIds, team.team_id]);
  };
  const removeTeamSelection = (id: string) => {
    onTeamIdsChange(selectedTeamIds.filter(tid => tid !== id));
  };

  const teamPagination = (
    <PaginationComponentV2
      fetch={searchTeams}
      searchPaginationInput={teamSearchPagination}
      setContent={setTeams}
      setLoading={setIsLoadingTeams}
      entityPrefix="team"
      availableFilterNames={['team_tags']}
      queryableHelpers={teamQueryableHelpers}
    />
  );

  // -- Persons tab --
  const [players, setPlayers] = useState<PlayerOutput[]>([]);
  const [isLoadingPlayers, setIsLoadingPlayers] = useState(false);

  const { queryableHelpers: playerQueryableHelpers, searchPaginationInput: playerSearchPagination }
    = useQueryable(buildSearchPagination({}));

  const playerLabel = useCallback((player: PlayerOutput) => {
    const name = `${player.user_firstname ?? ''} ${player.user_lastname ?? ''}`.trim();
    return name.length > 0 ? name : player.user_email;
  }, []);

  const playerElements: ClickableListElements<PlayerOutput> = useMemo(() => ({
    icon: { value: () => <PersonOutlined color="primary" /> },
    headers: [
      {
        field: 'user_email',
        value: (player: PlayerOutput) => <>{playerLabel(player)}</>,
        width: 100,
      },
    ],
  }), [playerLabel]);

  const addPlayerSelection = (_id: string, player: PlayerOutput) => {
    onPlayerIdsChange([...selectedPlayerIds, player.user_id]);
  };
  const removePlayerSelection = (id: string) => {
    onPlayerIdsChange(selectedPlayerIds.filter(pid => pid !== id));
  };

  const playerPagination = (
    <PaginationComponentV2
      fetch={searchPlayers}
      searchPaginationInput={playerSearchPagination}
      setContent={setPlayers}
      setLoading={setIsLoadingPlayers}
      entityPrefix="user"
      availableFilterNames={['user_tags']}
      queryableHelpers={playerQueryableHelpers}
    />
  );

  const handleTabChange = useCallback((_e: SyntheticEvent, newValue: string) => {
    setCurrentTab(newValue);
  }, []);

  const handleDownloadTemplate = useCallback(() => {
    download(buildScopeRulesCsvTemplate(), 'scope-rules-template.csv', 'text/csv;charset=utf-8');
  }, []);

  const handleUploadCsv = useCallback(
    async (_formData: FormData, file: File) => {
      const content = await file.text();
      let result: ReturnType<typeof parseScopeRulesCsv>;
      try {
        result = parseScopeRulesCsv(content);
      } catch {
        MESSAGING$.notifyError(t('CSV import failed'));
        return;
      }

      if (result.valid.length > 0) {
        const existingKeys = new Set(
          selectedCustomRules.map(rule => `${rule.source}:${rule.value.toLowerCase()}`),
        );
        const importedRules: ScopeCustomRule[] = result.valid
          .map(rule => ({
            source: 'CSV' as const,
            value: rule.value.trim(),
          }))
          .filter(rule => !existingKeys.has(`${rule.source}:${rule.value.toLowerCase()}`));

        if (importedRules.length > 0) {
          onCustomRulesChange([...selectedCustomRules, ...importedRules]);
        }
      }

      if (result.invalid.length > 0) {
        const preview = result.invalid.slice(0, 3).map(err => `${t('Row')} ${err.row}: ${err.reason}`).join(' | ');
        MESSAGING$.notifyError(`${t('Some CSV rows are invalid')}: ${preview}`);
      } else if (result.valid.length > 0) {
        MESSAGING$.notifySuccess(t('CSV imported successfully'));
      }
    },
    [onCustomRulesChange, selectedCustomRules, t],
  );

  const handleAddManual = useCallback((values: string[]) => {
    const existingKeys = new Set(
      selectedCustomRules.map(rule => `${rule.source}:${rule.value.toLowerCase()}`),
    );
    const newRules: ScopeCustomRule[] = values
      .map(v => ({
        source: 'MANUAL' as const,
        value: v.trim(),
      }))
      .filter(rule => rule.value.length > 0 && !existingKeys.has(`${rule.source}:${rule.value.toLowerCase()}`));
    if (newRules.length > 0) {
      onCustomRulesChange([...selectedCustomRules, ...newRules]);
    }
  }, [onCustomRulesChange, selectedCustomRules]);

  const handleClearAll = useCallback(() => {
    onEndpointIdsChange([]);
    onAssetGroupIdsChange([]);
    onTeamIdsChange([]);
    onPlayerIdsChange([]);
    onCustomRulesChange([]);
  }, [onEndpointIdsChange, onAssetGroupIdsChange, onTeamIdsChange, onPlayerIdsChange, onCustomRulesChange]);

  const removeCustomRule = useCallback((ruleToRemove: ScopeCustomRule) => {
    onCustomRulesChange(
      selectedCustomRules.filter(
        item => item.source !== ruleToRemove.source || item.value !== ruleToRemove.value,
      ),
    );
  }, [onCustomRulesChange, selectedCustomRules]);

  const inventoryChips = useMemo(() => {
    const endpointChips = selectedEndpoints.map(ep => ({
      key: `endpoint-${ep.asset_id}`,
      label: ep.asset_name,
      onDelete: () => removeEndpoint(ep.asset_id),
    }));

    const assetGroupChips = selectedAssetGroups.map(ag => ({
      key: `asset-group-${ag.asset_group_id}`,
      label: ag.asset_group_name,
      onDelete: () => removeAssetGroup(ag.asset_group_id),
    }));

    const teamChips = selectedTeamOptions.map(team => ({
      key: `team-${team.id}`,
      label: team.label,
      onDelete: () => removeTeamSelection(team.id),
    }));

    const playerChips = selectedPlayerOptions.map(player => ({
      key: `player-${player.id}`,
      label: player.label,
      onDelete: () => removePlayerSelection(player.id),
    }));

    const customRuleChips = selectedCustomRules.map(rule => ({
      key: `custom-${rule.source}-${rule.value}`,
      label: rule.value,
      onDelete: () => removeCustomRule(rule),
    }));

    return [...endpointChips, ...assetGroupChips, ...teamChips, ...playerChips, ...customRuleChips];
  }, [
    onCustomRulesChange,
    selectedAssetGroups,
    selectedTeamOptions,
    selectedPlayerOptions,
    selectedCustomRules,
    selectedEndpoints,
    t,
    removeCustomRule,
  ]);

  return (
    <Box sx={{
      display: 'grid',
      gap: theme.spacing(3),
    }}
    >
      <ScopeInventoryBox
        listLabel={listLabel}
        totalSelected={totalSelected}
        chips={inventoryChips}
        onDownloadTemplate={handleDownloadTemplate}
        onUploadCsv={handleUploadCsv}
        onAddManual={handleAddManual}
        onClearAll={handleClearAll}
      />

      {/* Add section */}
      <Box>
        <SectionLabel>{addLabel}</SectionLabel>

        <Box>
          <Tabs value={currentTab} onChange={handleTabChange}>
            <Tab value="assets" label={t('Assets')} />
            <Tab value="asset_groups" label={t('Asset groups')} />
            <Tab value="teams" label={t('Teams')} />
            <Tab value="persons" label={t('Persons')} />
          </Tabs>
        </Box>

        <Box sx={{ marginTop: theme.spacing(2) }}>
          {currentTab === 'assets' && (
            <ClickableList<EndpointOutput>
              values={endpoints}
              selectedIds={selectedEndpointIds}
              elements={endpointElements}
              onSelect={addEndpoint}
              onDeselect={removeEndpoint}
              paginationComponent={endpointPagination}
              getId={el => el.asset_id}
              isLoading={isLoadingEndpoints}
            />
          )}

          {currentTab === 'asset_groups' && (
            <ClickableList<AssetGroupOutput>
              values={assetGroups}
              selectedIds={selectedAssetGroupIds}
              elements={assetGroupElements}
              onSelect={addAssetGroup}
              onDeselect={removeAssetGroup}
              paginationComponent={assetGroupPagination}
              getId={el => el.asset_group_id}
              isLoading={isLoadingAssetGroups}
            />
          )}

          {currentTab === 'teams' && (
            <ClickableList<TeamOutput>
              values={teams}
              selectedIds={selectedTeamIds}
              elements={teamElements}
              onSelect={addTeamSelection}
              onDeselect={removeTeamSelection}
              paginationComponent={teamPagination}
              getId={el => el.team_id}
              isLoading={isLoadingTeams}
            />
          )}

          {currentTab === 'persons' && (
            <ClickableList<PlayerOutput>
              values={players}
              selectedIds={selectedPlayerIds}
              elements={playerElements}
              onSelect={addPlayerSelection}
              onDeselect={removePlayerSelection}
              paginationComponent={playerPagination}
              getId={el => el.user_id}
              isLoading={isLoadingPlayers}
            />
          )}
        </Box>
      </Box>

      {/* Footer buttons */}
      <Box sx={{
        display: 'flex',
        justifyContent: 'flex-end',
        gap: theme.spacing(1),
      }}
      >
        <Button
          variant="outlined"
          color="primary"
          onClick={onCancel}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="primary"
          onClick={onSubmit}
          disabled={!hasChanges}
        >
          {t('Define scope')}
        </Button>
      </Box>
    </Box>
  );
};

export default ScopeForm;
