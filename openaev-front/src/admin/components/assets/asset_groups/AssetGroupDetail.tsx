import { DevicesOtherOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Binoculars, SelectGroup } from 'mdi-material-ui';
import { type CSSProperties, useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { fetchAssetGroup, searchEndpointsFromAssetGroup, searchInjectsForAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import { type AssetGroupsHelper } from '../../../../actions/asset_groups/assetgroup-helper';
import { searchDistinctFindings } from '../../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, HeroStat, InformationGrid, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import AssetTypeFragment from '../../../../components/common/list/fragments/AssetTypeFragment';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { initSorting, type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../components/common/SortHeadersList';
import Tabs from '../../../../components/common/tabs/Tabs';
import useRoutedTabs from '../../../../components/common/tabs/useRoutedTabs';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemCriticality from '../../../../components/ItemCriticality';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import { ASSET_BASE_URL, ASSET_GROUP_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import { type AssetOutput, type Filter, type InjectResultOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import InjectResultList from '../../atomic_testings/InjectResultList';
import injectResultDetailPath from '../../atomic_testings/injectResultUtils';
import AutonomousAttackCreation from '../../autonomous/AutonomousAttackCreation';
import FindingList from '../../findings/FindingList';
import EntityReportsPanel from '../../reporting/EntityReportsPanel';
import AssetCategoryIcon from '../AssetCategoryIcon';
import ExpectationList from '../ExpectationList';
import PostureScore from '../PostureScore';
import InjectsPlayedOverTimeChart from '../statistics/InjectsPlayedOverTimeChart';
import PostureScoreOverTimeChart from '../statistics/PostureScoreOverTimeChart';
import useExpectationPosture from '../useExpectationPosture';
import AssetGroupPopover from './AssetGroupPopover';
import computeRuleValues from './assetGroupRules';

const inlineStyles: Record<string, CSSProperties> = {
  asset_name: { width: '40%' },
  asset_type: { width: '20%' },
  asset_criticality: { width: '20%' },
  asset_tags: { width: '20%' },
};

// Adds a scoped filter to a search input without mutating the caller's group.
const withFilter = (input: SearchPaginationInput, key: string, values: string[]): SearchPaginationInput => {
  const filter: Filter = {
    id: generateFilterId(),
    key,
    mode: 'or',
    operator: 'contains',
    values,
  };
  return {
    ...input,
    filterGroup: {
      mode: input.filterGroup?.mode ?? 'and',
      filters: [...(input.filterGroup?.filters ?? []), filter],
    },
  };
};

const AssetGroupDetail = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const bodyItemsStyles = useBodyItemsStyles();
  const { assetGroupId } = useParams() as { assetGroupId: string };

  // Headline hero counts: size-1 probes of the group-scoped searches feeding the
  // lists below, plus the expectation posture from the dashboard engine. The
  // inject search covers atomic testings AND simulation injects (direct targeting
  // or execution evidence), so it stays consistent with the posture score.
  const injectsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => searchInjectsForAssetGroup(assetGroupId, input),
    [assetGroupId],
  ));
  const findingsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => searchDistinctFindings(withFilter(input, 'finding_asset_groups', [assetGroupId])),
    [assetGroupId],
  ));
  const posture = useExpectationPosture('base_asset_group_side', assetGroupId);

  // Overview keeps the current detail sections; Statistics adds the over-time
  // charts without eating vertical space on the main tab. Tabs are routed
  // (/admin/asset_groups/:id[/statistics]) so they can be deep-linked like the
  // atomic testing and scenario tabs.
  const { currentTab, handleChangeTab } = useRoutedTabs(['overview', 'statistics'], 'overview');

  // Injects played: the same scoped search as the hero count above, but
  // server-paginated for the full list section below.
  const { queryableHelpers: injectsHelpers, searchPaginationInput: injectsInput } = useQueryableWithLocalStorage(
    'asset-group-injects',
    buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }),
  );
  const fetchInjectsPlayed = useCallback(
    (input: SearchPaginationInput): Promise<{ data: Page<InjectResultOutput> }> =>
      searchInjectsForAssetGroup(assetGroupId, input) as Promise<{ data: Page<InjectResultOutput> }>,
    [assetGroupId],
  );

  // Fetching data
  const { assetGroup } = useHelper((helper: AssetGroupsHelper) => ({ assetGroup: helper.getAssetGroup(assetGroupId) }));
  useDataLoader(() => {
    dispatch(fetchAssetGroup(assetGroupId));
  }, [assetGroupId]);

  // Member assets pagination
  const [endpoints, setEndpoints] = useState<AssetOutput[]>([]);
  const [reloadContentCount, setReloadContentCount] = useState(0);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'asset-group-detail-assets',
    buildSearchPagination({ sorts: initSorting('asset_name') }),
  );
  const availableFilterNames = [
    'endpoint_platform',
    'endpoint_arch',
    'asset_tags',
  ];

  const headers: Header[] = [
    {
      field: 'asset_name',
      label: 'Name',
      isSortable: true,
      value: (asset: AssetOutput) => asset.asset_name,
    },
    {
      field: 'asset_type',
      label: 'Type',
      isSortable: false,
      value: (asset: AssetOutput) => <AssetTypeFragment type={asset.asset_type} category={asset.asset_category} />,
    },
    {
      field: 'asset_criticality',
      label: 'Criticality',
      isSortable: false,
      value: (asset: AssetOutput) => <ItemCriticality criticality={asset.asset_criticality} />,
    },
    {
      field: 'asset_tags',
      label: 'Tags',
      isSortable: false,
      value: (asset: AssetOutput) => <ItemTags variant="list" tags={asset.asset_tags} />,
    },
  ];

  if (!assetGroup) {
    return <Loader />;
  }

  // Static members plus dynamic-rule matches, deduplicated: a group defined
  // only by dynamic rules would otherwise show 0 assets in the hero while the
  // member list below (which resolves dynamic members) shows rows.
  const memberCount = new Set([
    ...(assetGroup.asset_group_assets ?? []),
    ...(assetGroup.asset_group_dynamic_assets ?? []),
  ]).size;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
    }}
    >
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Asset groups'),
            link: ASSET_GROUP_BASE_URL,
          },
          {
            label: assetGroup.asset_group_name,
            current: true,
          },
        ]}
      />

      <DetailHero
        icon={SelectGroup}
        title={assetGroup.asset_group_name}
        action={(
          <>
            {/* Autonomous (AI-driven) attack scoped to this group - self-hides
                unless the preview feature is on and XTM One is configured. */}
            <AutonomousAttackCreation
              variant="icon"
              presetScopeAssetGroupId={assetGroupId}
              presetScopeAssetGroupName={assetGroup.asset_group_name}
            />
            {/* Entity-scoped reports - self-hides without the reporting
                access capability. */}
            <EntityReportsPanel
              contextType="ASSET_GROUP"
              contextId={assetGroupId}
              entityName={assetGroup.asset_group_name}
            />
            <AssetGroupPopover
              assetGroup={assetGroup}
              onUpdate={() => {
                dispatch(fetchAssetGroup(assetGroupId));
                setReloadContentCount(count => count + 1);
              }}
              onDelete={() => navigate(ASSET_GROUP_BASE_URL)}
            />
          </>
        )}
        stats={(
          <>
            <HeroStat icon={DevicesOtherOutlined} label={t('Assets')} value={memberCount} color={theme.palette.success.main} />
            <HeroStat icon={Binoculars} label={t('Findings')} value={findingsTotal ?? '-'} color={theme.palette.primary.main} />
            <HeroStat icon={TrackChangesOutlined} label={t('Injects played')} value={injectsTotal ?? '-'} color={theme.palette.warning.main} />
            <HeroStat icon={TrackChangesOutlined} label={t('Expectations tested')} value={posture.loading ? '-' : posture.tested} />
            <PostureScore
              scope="asset-group"
              success={posture.success}
              failed={posture.failed}
              breakdown={posture.breakdown}
              loading={posture.loading}
            />
          </>
        )}
      />

      <Tabs
        entries={[
          {
            key: 'overview',
            label: t('Overview'),
          },
          {
            key: 'statistics',
            label: t('Statistics'),
          },
        ]}
        currentTab={currentTab}
        onChange={handleChangeTab}
      />

      {currentTab === 'statistics' && (
        // Two simple 50/50 time series: the posture-score evolution (same
        // formula as the hero gauge) and the injects-played activity.
        <DetailSections>
          <SectionBlock title={t('Posture score over time')}>
            <PostureScoreOverTimeChart scopeField="base_asset_group_side" entityId={assetGroupId} height={280} />
          </SectionBlock>
          <SectionBlock title={t('Injects played over time')}>
            <InjectsPlayedOverTimeChart scopeField="base_asset_groups_side" entityId={assetGroupId} height={280} />
          </SectionBlock>
        </DetailSections>
      )}

      {currentTab === 'overview' && (
        <>
          {/* Identity + dynamic rules side by side: both sections are short, so
          sharing one grid row keeps the overview compact. */}
          <DetailSections>
            <InformationGrid title={t('Information')}>
              <Field label={t('Description')}>
                {assetGroup.asset_group_description
                  ? <ExpandableMarkdown source={assetGroup.asset_group_description} limit={300} />
                  : '-'}
              </Field>
              <Field label={t('Tags')}>
                <ItemTags variant="list" tags={assetGroup.asset_group_tags} />
              </Field>
            </InformationGrid>
            <SectionBlock title={t('Rules')}>
              {computeRuleValues(assetGroup, t)}
            </SectionBlock>
          </DetailSections>

          <SectionBlock title={t('Assets')}>
            <PaginationComponentV2
              fetch={(input: SearchPaginationInput) => searchEndpointsFromAssetGroup(input, assetGroupId)}
              searchPaginationInput={searchPaginationInput}
              setContent={setEndpoints}
              entityPrefix="endpoint"
              availableFilterNames={availableFilterNames}
              queryableHelpers={queryableHelpers}
              reloadContentCount={reloadContentCount}
              contextId={assetGroupId}
            />
            <List>
              <ListItem style={{
                paddingTop: 0,
                textTransform: 'uppercase',
              }}
              >
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
              </ListItem>
              {endpoints.length > 0
                ? endpoints.map(asset => (
                    <ListItem key={asset.asset_id} divider disablePadding data-testid="asset-group-asset-row">
                      <ListItemButton
                        component={Link}
                        to={`${ASSET_BASE_URL}/${asset.asset_id}`}
                        sx={{ height: 50 }}
                      >
                        <ListItemIcon>
                          <AssetCategoryIcon category={asset.asset_category} color="primary" />
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
                                  {header.value?.(asset)}
                                </div>
                              ))}
                            </div>
                          )}
                        />
                      </ListItemButton>
                    </ListItem>
                  ))
                : <Empty message={t('No asset in this asset group.')} />}
            </List>
          </SectionBlock>

          <SectionBlock title={t('Findings')}>
            <FindingList
              filterLocalStorageKey="asset-group-findings"
              searchDistinctFindings={(input: SearchPaginationInput) => searchDistinctFindings(withFilter(input, 'finding_asset_groups', [assetGroupId]))}
              contextId={assetGroupId}
            />
          </SectionBlock>

          <SectionBlock title={t('Expectations')}>
            {/* Every expectation evaluated against this asset group - same
                scope as the posture score and the hero counter, so the KPIs
                and the list stay consistent. */}
            <ExpectationList
              filterLocalStorageKey="asset-group-expectations"
              scopeField="base_asset_group_side"
              entityId={assetGroupId}
            />
          </SectionBlock>

          <SectionBlock title={t('Injects played')}>
            <InjectResultList
              fetchInjects={fetchInjectsPlayed}
              goTo={injectResultDetailPath}
              queryableHelpers={injectsHelpers}
              searchPaginationInput={injectsInput}
              contextId={assetGroupId}
            />
          </SectionBlock>
        </>
      )}
    </Box>
  );
};

export default AssetGroupDetail;
