import { HelpOutlineOutlined } from '@mui/icons-material';
import {
  Box,
  Checkbox,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  ToggleButtonGroup,
} from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { bulkDeleteAssets, searchAssets } from '../../../../actions/assets/endpoint-actions';
import { fetchExecutors } from '../../../../actions/executors/executor-action';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import ExportButton from '../../../../components/common/ExportButton';
import AssetPlatformFragment from '../../../../components/common/list/fragments/AssetPlatformFragment';
import EndpointActiveFragment from '../../../../components/common/list/fragments/EndpointActiveFragment';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../components/i18n';
import ItemCriticality from '../../../../components/ItemCriticality';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { ASSET_BASE_URL } from '../../../../constants/BaseUrls';
import { type EndpointOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import { Can, useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import EndpointListItemFragments from '../../common/endpoints/EndpointListItemFragments';
import EndpointAgentsExecutorsFragment from '../../common/endpoints/fragments/EndpointAgentsExecutorsFragment';
import ToolBar from '../../common/ToolBar';
import { humanizeEnum } from '../asset-categories';
import AssetCategoryIcon from '../AssetCategoryIcon';
import PostureScoreCell from '../PostureScoreCell';
import usePostureScores from '../usePostureScores';
import AssetPopover from './AssetPopover';
import EndpointCreation from './EndpointCreation';
import ImportUploaderEndpoints from './ImportUploaderEndpoints';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  asset_name: { width: '20%' },
  asset_category: { width: '12%' },
  endpoint_active: { width: '9%' },
  endpoint_platform: { width: '9%' },
  endpoint_agents_executor: { width: '12%' },
  asset_criticality: { width: '9%' },
  asset_posture: { width: '10%' },
  asset_tags: { width: '19%' },
};

const Endpoints = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  // Load the executors once for the whole page; the per-row Executors column
  // reads them from the store (previously each row fetched them, firing
  // GET /api/executors once per endpoint).
  useDataLoader(() => {
    dispatch(fetchExecutors());
  });

  // Query param
  const [searchParams] = useSearchParams();
  const [search] = searchParams.getAll('search');

  // Base Asset facets only: the unified inventory queries the base assets table, so endpoint-only
  // fields (platform / arch) cannot be resolved as filters here (single-table inheritance).
  const availableFilterNames = [
    'asset_category',
    'asset_subcategory',
    'asset_status',
    'asset_criticality',
    'asset_cloud_provider',
    'asset_internet_facing',
    'asset_tags',
  ];

  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('asset', buildSearchPagination({
    sorts: initSorting('asset_name'),
    textSearch: search,
  }));

  // Export
  const exportProps = {
    exportType: 'ENDPOINTS',
    exportKeys: [],
    exportData: endpoints,
    exportFileName: `${t('Endpoints')}.csv`,
    searchPaginationInput: searchPaginationInput,
  };

  const [loading, setLoading] = useState<boolean>(true);

  const searchEndpointsToLoad = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchAssets(input).finally(() => setLoading(false));
  };

  // Bulk selection
  const ability = useAbility();
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSETS);
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<EndpointOutput>('asset', endpoints, queryableHelpers.paginationHelpers.getTotalElements());

  // Per-row posture score, batched in a single dashboard-engine query per page.
  const { loading: postureLoading, scores: postureScores } = usePostureScores(
    'base_asset_side',
    endpoints.map(endpoint => endpoint.asset_id),
  );

  const bulkDelete = () => {
    bulkDeleteAssets({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      asset_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      asset_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setEndpoints(endpoints.filter(e => !deletedIds.includes(e.asset_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  // Headers
  const headers = [
    {
      field: EndpointListItemFragments.ASSET_NAME,
      label: 'Name',
      isSortable: true,
      value: (endpoint: EndpointOutput) => endpoint.asset_name,
    },
    {
      field: 'asset_category',
      label: 'Category',
      isSortable: true,
      value: (endpoint: EndpointOutput) => (
        <span style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          <AssetCategoryIcon category={endpoint.asset_category} fontSize="small" />
          {endpoint.asset_category ? t(humanizeEnum(endpoint.asset_category)) : '-'}
        </span>
      ),
    },
    {
      field: EndpointListItemFragments.ENDPOINT_ACTIVE,
      label: 'Status',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <EndpointActiveFragment activity_map={(endpoint.asset_agents ?? []).map(a => a.agent_active ?? false)} />,
    },
    {
      field: EndpointListItemFragments.ENDPOINT_PLATFORM,
      label: 'Platform',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <AssetPlatformFragment platform={endpoint.endpoint_platform} />,
    },
    {
      field: 'endpoint_agents_executor',
      label: 'Executors',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <EndpointAgentsExecutorsFragment endpoint={endpoint} />,
    },
    {
      field: 'asset_criticality',
      label: 'Criticality',
      isSortable: true,
      value: (endpoint: EndpointOutput) => <ItemCriticality criticality={endpoint.asset_criticality} />,
    },
    {
      field: 'asset_posture',
      label: 'Posture score',
      isSortable: false,
      value: (endpoint: EndpointOutput) => (
        <PostureScoreCell
          success={postureScores[endpoint.asset_id]?.success ?? 0}
          failed={postureScores[endpoint.asset_id]?.failed ?? 0}
          loading={postureLoading}
        />
      ),
    },
    {
      field: EndpointListItemFragments.ASSET_TAGS,
      label: 'Tags',
      isSortable: false,
      value: (endpoint: EndpointOutput) => <ItemTags variant="list" tags={endpoint.asset_tags ?? []} />,
    },
  ];

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Endpoints'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={searchEndpointsToLoad}
        searchPaginationInput={searchPaginationInput}
        setContent={setEndpoints}
        entityPrefix="asset"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            <ToggleButtonGroup value="fake" exclusive>
              <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
                <ImportUploaderEndpoints />
              </Can>
            </ToggleButtonGroup>
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
              <EndpointCreation onCreate={result => setEndpoints([result as EndpointOutput, ...endpoints])} agentless={true} />
            </Can>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          sx={numberOfSelectedElements > 0
            ? {
                // Massive-operations toolbar: symmetric vertical padding keeps the
                // checkbox and actions vertically centered in the accent band.
                backgroundColor: 'background.accent',
                paddingBlock: 0.5,
              }
            : { paddingTop: 0 }}
          {...(numberOfSelectedElements === 0 ? { secondaryAction: <>&nbsp;</> } : {})}
        >
          {canManage && (
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
              />
            </ListItemIcon>
          )}
          {numberOfSelectedElements > 0 ? (
            <ListItemText
              primary={(
                <ToolBar
                  numberOfSelectedElements={numberOfSelectedElements}
                  handleClearSelectedElements={handleClearSelectedElements}
                  handleBulkDelete={bulkDelete}
                  canManage={canManage}
                  deleteConfirmationSingular={t('Do you want to delete this asset?')}
                  deleteConfirmationPlural={t('Do you want to delete these {count} assets?', { count: String(numberOfSelectedElements) })}
                />
              )}
            />
          ) : (
            <>
              <ListItemIcon />
              <ListItemText
                primary={(
                  <SortHeadersComponentV2
                    headers={headers}
                    inlineStylesHeaders={inlineStyles}
                    sortHelpers={queryableHelpers.sortHelpers}
                  />
                )}
              />
            </>
          )}
        </ListItem>
        {
          loading
            ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox={canManage} />
            : endpoints.map((endpoint: EndpointOutput) => {
                // Every asset type now has a generic detail page, and the popover renders on every
                // row so the secondary-action column stays consistent and the columns stay aligned.
                return (
                  <ListItem
                    key={endpoint.asset_id}
                    divider
                    secondaryAction={(
                      <AssetPopover
                        inline
                        endpoint={{ ...endpoint }}
                        agentless={endpoint.asset_agents?.length === 0}
                        onUpdate={result => setEndpoints(endpoints.map(e => (e.asset_id !== result.asset_id ? e : result as EndpointOutput)))}
                        onDelete={result => setEndpoints(endpoints.filter(e => (e.asset_id !== result)))}
                      />
                    )}
                    disablePadding
                  >
                    <ListItemButton component={Link} to={`${ASSET_BASE_URL}/${endpoint.asset_id}`} classes={{ root: classes.item }}>
                      {canManage && (
                        <ListItemIcon
                          style={{ minWidth: 40 }}
                          onClick={event => onToggleEntity(endpoint, event)}
                        >
                          <Checkbox
                            edge="start"
                            checked={
                              (selectAll && !(endpoint.asset_id in (deSelectedElements || {})))
                              || endpoint.asset_id in (selectedElements || {})
                            }
                            disableRipple
                          />
                        </ListItemIcon>
                      )}
                      <ListItemIcon>
                        <AssetCategoryIcon category={endpoint.asset_category} color="primary" />
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div style={bodyItemsStyles.bodyItems}>
                            {headers.map(header => (
                              <div
                                key={header.field}
                                style={{
                                  ...bodyItemsStyles.bodyItem,
                                  ...inlineStyles[header.field],
                                }}
                              >
                                {header.value(endpoint)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })
        }
      </List>
    </>
  );
};

export default Endpoints;
