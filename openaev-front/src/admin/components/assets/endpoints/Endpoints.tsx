import { Checkbox } from '@filigran/design-system';
import { CenterFocusStrongOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { bulkDeleteAssets, bulkReplaceAssetMarkings, searchAssets } from '../../../../actions/assets/endpoint-actions';
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
import ItemMarkings from '../../../../components/ItemMarkings';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { ASSET_BASE_URL } from '../../../../constants/BaseUrls';
import { type EndpointOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useEntityToggle from '../../../../utils/hooks/useEntityToggle';
import useMarkingDefinitions from '../../../../utils/hooks/useMarkingDefinitions';
import { Can, useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import EndpointListItemFragments from '../../common/endpoints/EndpointListItemFragments';
import EndpointAgentsExecutorsFragment from '../../common/endpoints/fragments/EndpointAgentsExecutorsFragment';
import ToolBar from '../../common/ToolBar';
import { humanizeEnum } from '../asset-categories';
import AssetCategoryIcon from '../AssetCategoryIcon';
import PostureScoreCell from '../PostureScoreCell';
import usePostureScores from '../usePostureScores';
import AssetPopover from './AssetPopover';
import BulkMarkingsDrawer from './BulkMarkingsDrawer';
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
  asset_tags: { width: '10%' },
  asset_markings: { width: '9%' },
};

const Endpoints = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useAbility();
  // ACCESS_MARKING_DEFINITION and marking assignment (ASSIGN_MARKING / DELETE_MARKING_ASSIGNMENT,
  // enforced server-side on the asset WRITE endpoints) are independent capabilities - a user can
  // hold either without the other. Three separate gates, each matching what the request it guards
  // actually requires server-side:
  //  - markingEnabled: row display and the bulk picker, which only ever call the capability-free
  //    /assignable endpoint (a row's own markings are always within the viewer's clearance already,
  //    see useMarkingDefinitions's doc).
  //  - canAccessMarkingDefinitions: the asset_markings filter, whose options come from
  //    /marking_definitions/options|search, which does require ACCESS_MARKING_DEFINITION.
  //  - canManage (MANAGE_ASSETS, i.e. asset WRITE): the bulk "Edit markings" action, mirroring the
  //    @AccessControl on PUT /api/assets/markings/bulk; the finer-grained ASSIGN_MARKING /
  //    DELETE_MARKING_ASSIGNMENT check depends on the payload and is only enforced server-side.
  const markingEnabled = isFeatureEnabled('MARKING');
  const canAccessMarkingDefinitions = markingEnabled && ability.can(ACTIONS.ACCESS, SUBJECTS.MARKING_DEFINITION);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSETS);
  const canBulkEditMarkings = markingEnabled && canManage;
  // Resolved once for the whole page; the Markings column maps ids per row. Needs no capability,
  // only the feature flag - see the gating note above.
  const markingDefinitions = useMarkingDefinitions({
    skip: !markingEnabled,
    assignableOnly: true,
  });

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
    ...(canAccessMarkingDefinitions ? ['asset_markings'] : []),
  ];

  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
  const [reloadCount, setReloadCount] = useState<number>(0);
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

  // Bulk marking replacement: every selected asset ends up carrying exactly the chosen markings
  // server-side (see bulkReplaceAssetMarkings's backend doc). Unlike bulkDelete, this cannot be
  // mirrored by patching `endpoints` in place: the new markings can push a row out of (or into) an
  // active asset_markings filter, which a local patch has no way to detect - it would leave a
  // GREEN-filtered row displayed, and counted in the pagination total, after replacing it with
  // YELLOW. Bumping reloadCount re-runs the current search server-side instead, so the filter and
  // totalElements both reflect the new markings.
  const [openEditMarkings, setOpenEditMarkings] = useState(false);
  const bulkEditMarkings = (markingIds: string[]) => {
    return bulkReplaceAssetMarkings({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      asset_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      asset_ids_to_ignore: Object.keys(deSelectedElements),
      marking_ids: markingIds,
    }).then(() => {
      setOpenEditMarkings(false);
      handleClearSelectedElements();
      setReloadCount(count => count + 1);
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
          <span style={{
            width: 'fit-content',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
          >
            {endpoint.asset_category ? t(humanizeEnum(endpoint.asset_category)) : '-'}
          </span>
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
    // Rendered whenever MARKING is enabled, regardless of ACCESS_MARKING_DEFINITION: a row's
    // markings are already clearance-filtered server-side (see the gating note above), so there is
    // nothing left to gate here beyond the feature flag.
    ...(markingEnabled
      ? [{
          field: 'asset_markings',
          label: 'Markings',
          // Not sortable: markings are stored as a text[] on the row, not a joinable column.
          isSortable: false,
          value: (endpoint: EndpointOutput) => (
            <ItemMarkings
              variant="list"
              markingIds={endpoint.asset_markings ?? []}
              definitions={markingDefinitions}
            />
          ),
        }]
      : []),
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
        reloadContentCount={reloadCount}
        entityPrefix="asset"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box
            sx={{
              display: 'flex',
              gap: 1,
              alignItems: 'center',
            }}
          >
            {/* A plain row of actions: this was a ToggleButtonGroup used as a frame,
                which announced a group of choices that never existed. */}
            <Box sx={{
              display: 'flex',
              gap: 1,
            }}
            >
              <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
                <ImportUploaderEndpoints />
              </Can>
            </Box>
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
              <EndpointCreation
                onCreate={(result) => {
                  setEndpoints([result as EndpointOutput, ...endpoints]);
                  queryableHelpers.paginationHelpers.incrementTotalElements(1);
                }}
                onAiTargetCreate={() => setReloadCount(count => count + 1)}
                agentless={true}
              />
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
                aria-label={t('Select all')}
                checked={selectAll}
                onCheckedChange={handleToggleSelectAll}
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
                  toolTasks={canBulkEditMarkings
                    ? [{
                        type: 'edit-markings',
                        title: t('Edit markings'),
                        icon: () => <CenterFocusStrongOutlined fontSize="small" />,
                        onClick: () => setOpenEditMarkings(true),
                      }]
                    : []}
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
                        onDelete={(result) => {
                          setEndpoints(endpoints.filter(e => (e.asset_id !== result)));
                          queryableHelpers.paginationHelpers.incrementTotalElements(-1);
                        }}
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
                            aria-label={endpoint.asset_name}
                            checked={
                              (selectAll && !(endpoint.asset_id in (deSelectedElements || {})))
                              || endpoint.asset_id in (selectedElements || {})
                            }
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
      {canBulkEditMarkings && (
        <BulkMarkingsDrawer
          open={openEditMarkings}
          onClose={() => setOpenEditMarkings(false)}
          onSubmit={bulkEditMarkings}
        />
      )}
    </>
  );
};

export default Endpoints;
