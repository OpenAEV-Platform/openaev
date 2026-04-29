import { Close, DevicesOtherOutlined } from '@mui/icons-material';
import { Box, Button, Chip, IconButton, Tab, Tabs, Tooltip, Typography } from '@mui/material';
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
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../utils/url-helper';
import AssetStatus from '../assets/AssetStatus';

interface ScopeFormProps {
  mode: 'ALLOWLIST' | 'DENYLIST';
  selectedEndpointIds: string[];
  selectedAssetGroupIds: string[];
  initialEndpointIds: string[];
  initialAssetGroupIds: string[];
  onEndpointIdsChange: (ids: string[]) => void;
  onAssetGroupIdsChange: (ids: string[]) => void;
  onCancel: () => void;
  onSubmit: () => void;
}

const ScopeForm: FunctionComponent<ScopeFormProps> = ({
  mode,
  selectedEndpointIds,
  selectedAssetGroupIds,
  initialEndpointIds,
  initialAssetGroupIds,
  onEndpointIdsChange,
  onAssetGroupIdsChange,
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

  const totalSelected = selectedEndpointIds.length + selectedAssetGroupIds.length;

  const hasChanges = useMemo(() => {
    const sortedCurrent = [...selectedEndpointIds, ...selectedAssetGroupIds].sort();
    const sortedInitial = [...initialEndpointIds, ...initialAssetGroupIds].sort();
    if (sortedCurrent.length !== sortedInitial.length) return true;
    return sortedCurrent.some((id, i) => id !== sortedInitial[i]);
  }, [selectedEndpointIds, selectedAssetGroupIds, initialEndpointIds, initialAssetGroupIds]);

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

  return (
    <Box sx={{
      display: 'grid',
      gap: theme.spacing(3),
    }}
    >
      {/* Inventory section */}
      <Box>
        <Typography variant="h4">
          {`${listLabel} ${t('inventory')} (${totalSelected})`}
        </Typography>

        <Box
          sx={{
            position: 'relative',
            minHeight: 100,
            border: `1px solid ${theme.palette.divider}`,
            borderRadius: 1,
            padding: theme.spacing(2),
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'flex-start',
            alignContent: 'flex-start',
            gap: theme.spacing(1),
          }}
        >
          {totalSelected > 0 && (
            <Tooltip title={t('Clear all')}>
              <IconButton
                size="small"
                color="primary"
                onClick={() => {
                  onEndpointIdsChange([]);
                  onAssetGroupIdsChange([]);
                }}
                sx={{
                  position: 'absolute',
                  top: 4,
                  right: 4,
                }}
              >
                <Close fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          {totalSelected === 0 && (
            <Typography
              variant="body2"
              sx={{ color: 'text.disabled' }}
            >
              {t('No asset selected. Add asset manually or select some in the asset list.')}
            </Typography>
          )}
          {selectedEndpoints.map(ep => (
            <Chip
              key={ep.asset_id}
              label={ep.asset_name}
              size="small"
              onDelete={() => removeEndpoint(ep.asset_id)}
            />
          ))}
          {selectedAssetGroups.map(ag => (
            <Chip
              key={ag.asset_group_id}
              label={ag.asset_group_name}
              size="small"
              onDelete={() => removeAssetGroup(ag.asset_group_id)}
            />
          ))}
        </Box>
      </Box>

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
