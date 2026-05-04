import { DevicesOtherOutlined } from '@mui/icons-material';
import { Box, Button, Tab, Tabs, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { type FunctionComponent, type SyntheticEvent, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { findAssetGroups, searchAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { findEndpoints, searchEndpoints } from '../../../actions/assets/endpoint-actions';
import { fetchExecutors } from '../../../actions/executors/executor-action';
import type { ExecutorHelper } from '../../../actions/executors/executor-helper';
import ClickableList, { type ClickableListElements } from '../../../components/common/ClickableList';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import type { AssetGroupOutput, EndpointOutput } from '../../../utils/api-types';
import { getActiveMsgTooltip, getExecutorsCount } from '../../../utils/endpoints/utils';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../utils/url-helper';
import { download } from '../../../utils/utils';
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

interface ScopeFormProps {
  mode: 'ALLOWLIST' | 'DENYLIST';
  selectedEndpointIds: string[];
  selectedAssetGroupIds: string[];
  selectedCustomRules: ScopeCustomRule[];
  initialEndpointIds: string[];
  initialAssetGroupIds: string[];
  initialCustomRules: ScopeCustomRule[];
  onEndpointIdsChange: (ids: string[]) => void;
  onAssetGroupIdsChange: (ids: string[]) => void;
  onCustomRulesChange: (rules: ScopeCustomRule[]) => void;
  onCancel: () => void;
  onSubmit: () => void;
}

const ScopeForm: FunctionComponent<ScopeFormProps> = ({
  mode,
  selectedEndpointIds,
  selectedAssetGroupIds,
  selectedCustomRules,
  initialEndpointIds,
  initialAssetGroupIds,
  initialCustomRules,
  onEndpointIdsChange,
  onAssetGroupIdsChange,
  onCustomRulesChange,
  onCancel,
  onSubmit,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const listLabel = mode === 'ALLOWLIST' ? t('Allowlisted') : t('Denylisted');
  const addLabel = mode === 'ALLOWLIST' ? t('Add asset to your allowlist') : t('Add asset to your denylist');

  // Tab state
  const [currentTab, setCurrentTab] = useState<string>('assets');

  // -- Selected values (inventory) --
  const [selectedEndpoints, setSelectedEndpoints] = useState<EndpointOutput[]>([]);
  const [selectedAssetGroups, setSelectedAssetGroups] = useState<AssetGroupOutput[]>([]);

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

  const totalSelected = selectedEndpointIds.length + selectedAssetGroupIds.length + selectedCustomRules.length;

  const hasChanges = useMemo(() => {
    const sortedCurrent = [
      ...selectedEndpointIds,
      ...selectedAssetGroupIds,
      ...selectedCustomRules.map(r => `${r.source}:${r.value.toLowerCase()}`),
    ].sort((a, b) => a.localeCompare(b));
    const sortedInitial = [
      ...initialEndpointIds,
      ...initialAssetGroupIds,
      ...initialCustomRules.map(r => `${r.source}:${r.value.toLowerCase()}`),
    ].sort((a, b) => a.localeCompare(b));
    if (sortedCurrent.length !== sortedInitial.length) return true;
    return sortedCurrent.some((id, i) => id !== sortedInitial[i]);
  }, [
    selectedEndpointIds,
    selectedAssetGroupIds,
    selectedCustomRules,
    initialEndpointIds,
    initialAssetGroupIds,
    initialCustomRules,
  ]);

  // -- Assets tab (endpoints) --
  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  const [isLoadingEndpoints, setIsLoadingEndpoints] = useState(false);

  const { queryableHelpers: endpointQueryableHelpers, searchPaginationInput: endpointSearchPagination }
    = useQueryable(buildSearchPagination({}));

  const endpointElements: ClickableListElements<EndpointOutput> = useMemo(() => ({
    icon: { value: () => <DevicesOtherOutlined color="primary" /> },
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

  const handleTabChange = useCallback((_e: SyntheticEvent, newValue: string) => {
    setCurrentTab(newValue);
  }, []);

  const handleDownloadTemplate = useCallback(() => {
    download(buildScopeRulesCsvTemplate(), 'scope-rules-template.csv', 'text/csv;charset=utf-8');
  }, []);

  const handleUploadCsv = useCallback(
    async (_formData: FormData, file: File) => {
      const content = await file.text();
      const result = parseScopeRulesCsv(content);

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
    onCustomRulesChange([]);
  }, [onEndpointIdsChange, onAssetGroupIdsChange, onCustomRulesChange]);

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

    const customRuleChips = selectedCustomRules.map(rule => ({
      key: `custom-${rule.source}-${rule.value}`,
      label: rule.value,
      onDelete: () => removeCustomRule(rule),
    }));

    return [...endpointChips, ...assetGroupChips, ...customRuleChips];
  }, [
    onCustomRulesChange,
    selectedAssetGroups,
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
        <Typography variant="h4">
          {addLabel}
        </Typography>

        <Box>
          <Tabs value={currentTab} onChange={handleTabChange}>
            <Tab value="assets" label={t('Assets')} />
            <Tab value="asset_groups" label={t('Asset groups')} />
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
          variant="contained"
          onClick={onCancel}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="secondary"
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
