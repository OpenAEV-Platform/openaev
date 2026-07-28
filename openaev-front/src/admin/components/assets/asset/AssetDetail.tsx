import { DevicesOtherOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Alert, AlertTitle, Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Binoculars } from 'mdi-material-ui';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { fetchAssetOverview, searchInjectsForAsset } from '../../../../actions/assets/endpoint-actions';
import { searchDistinctFindingsOnEndpoint } from '../../../../actions/findings/finding-actions';
import { type UserHelper } from '../../../../actions/helper';
import { fetchPlayers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { DetailHero, DetailSections, Field, HeroStat, InformationGrid, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import EndpointArchFragment from '../../../../components/common/list/fragments/EndpointArchFragment';
import { type Page } from '../../../../components/common/queryable/Page';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import Tabs from '../../../../components/common/tabs/Tabs';
import useRoutedTabs from '../../../../components/common/tabs/useRoutedTabs';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemCriticality from '../../../../components/ItemCriticality';
import ItemTags from '../../../../components/ItemTags';
import Loader from '../../../../components/Loader';
import PlatformIcon from '../../../../components/PlatformIcon';
import { useHelper } from '../../../../store';
import {
  type EndpointOverviewOutput,
  type InjectResultOutput,
  type SearchPaginationInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchTotal from '../../../../utils/hooks/useSearchTotal';
import { emptyFilled, formatIp, formatMacAddress } from '../../../../utils/String';
import InjectResultList from '../../atomic_testings/InjectResultList';
import injectResultDetailPath from '../../atomic_testings/injectResultUtils';
import FindingList from '../../findings/FindingList';
import EntityReportsPanel from '../../reporting/EntityReportsPanel';
import { humanizeEnum } from '../asset-categories';
import AssetCategoryIcon from '../AssetCategoryIcon';
import AssetPopover, { type AssetPopoverProps } from '../endpoints/AssetPopover';
import AgentList from '../endpoints/endpoint/AgentList';
import PostureScore from '../PostureScore';
import InjectsPlayedOverTimeChart from '../statistics/InjectsPlayedOverTimeChart';
import PostureScoreOverTimeChart from '../statistics/PostureScoreOverTimeChart';
import useExpectationPosture from '../useExpectationPosture';

// The backend's EndpointOverviewOutput now also carries the AI target connection metadata; until
// the API types are regenerated (needs a backend restart) they are declared here so the page can
// render them. Regenerating later makes these redundant but harmless.
type AssetOverview = EndpointOverviewOutput & {
  ai_target_provider?: string;
  ai_target_modality?: string;
  ai_target_endpoint?: string;
  ai_target_model?: string;
  ai_target_system_prompt?: string;
};

const AssetDetail = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  // The generic detail page is mounted on two routes for back-compatibility (details/:assetId and
  // the legacy endpoints/:endpointId), so resolve the id from whichever param is present.
  const { assetId, endpointId } = useParams() as {
    assetId?: string;
    endpointId?: string;
  };
  const id = assetId ?? endpointId ?? '';

  const [asset, setAsset] = useState<AssetOverview | null>(null);
  const [loading, setLoading] = useState(true);

  const loadAsset = useCallback(() => {
    setLoading(true);
    fetchAssetOverview(id)
      .then((result: { data: AssetOverview }) => setAsset(result.data))
      .catch(() => setAsset(null))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    loadAsset();
  }, [loadAsset]);

  // Resolve the linked person (identity assets) to a readable name.
  const { usersMap } = useHelper((helper: UserHelper) => ({ usersMap: helper.getUsersMap() }));
  useDataLoader(() => {
    if (asset?.asset_linked_person) {
      dispatch(fetchPlayers());
    }
  }, [asset?.asset_linked_person]);
  const linkedPerson = asset?.asset_linked_person ? usersMap?.[asset.asset_linked_person] : undefined;
  const linkedPersonName = linkedPerson
    ? ([linkedPerson.user_firstname, linkedPerson.user_lastname].filter(Boolean).join(' ').trim() || linkedPerson.user_email)
    : asset?.asset_linked_person;

  // Injects played: every inject that concerned this asset (atomic testings and simulation
  // injects; targeted directly, through an asset group - static or dynamic - or evidenced by the
  // expectations persisted at execution time). Same scope as the posture score, so the counter,
  // the list and the expectation KPIs stay consistent.
  const { queryableHelpers: injectsHelpers, searchPaginationInput: injectsInput } = useQueryableWithLocalStorage(
    'asset-injects',
    buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }),
  );
  const fetchInjectsPlayed = useCallback((input: SearchPaginationInput): Promise<{ data: Page<InjectResultOutput> }> => {
    return searchInjectsForAsset(id, input) as Promise<{ data: Page<InjectResultOutput> }>;
  }, [id]);

  // Headline hero counts: size-1 probes of the same searches feeding the lists
  // below, plus the expectation posture aggregated by the dashboard engine.
  const findingsTotal = useSearchTotal(useCallback(
    (input: SearchPaginationInput) => searchDistinctFindingsOnEndpoint(id, input),
    [id],
  ));
  const injectsTotal = useSearchTotal(fetchInjectsPlayed);
  const posture = useExpectationPosture('base_asset_side', id);

  // Overview keeps the current detail sections; Statistics adds the over-time
  // charts without eating vertical space on the main tab. Tabs are routed
  // (/admin/assets/:id[/statistics]) so they can be deep-linked like the
  // atomic testing and scenario tabs.
  const { currentTab, handleChangeTab } = useRoutedTabs(['overview', 'statistics'], 'overview');

  if (loading && !asset) {
    return <Loader />;
  }
  if (!asset) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Asset is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }

  const isAiTarget = asset.asset_category === 'AI_TARGET';

  let internetFacingLabel = '-';
  if (asset.asset_internet_facing != null) {
    internetFacingLabel = asset.asset_internet_facing ? t('Yes') : t('No');
  }

  // Value-driven field visibility keeps each asset kind showing only its relevant data.
  const hasNetwork = !!(asset.asset_hostname || asset.asset_seen_ip || asset.endpoint_platform
    || asset.endpoint_arch || (asset.asset_ips && asset.asset_ips.length) || (asset.asset_mac_addresses && asset.asset_mac_addresses.length));
  const hasCloud = !!asset.asset_cloud_provider;
  const hasAgents = !!(asset.asset_agents && asset.asset_agents.length > 0);
  // Only host-like categories can carry an OpenAEV agent (same taxonomy split
  // as TargetIcon's OS_PLATFORM_CATEGORIES). Agentless kinds (AI target, web
  // application, cloud resource, identity, ...) hide the Agents hero stat
  // entirely - a permanent 0 there is just noise. No category means legacy
  // endpoint data, which is host-like; and if agents somehow exist, show them.
  const supportsAgents = !asset.asset_category
    || asset.asset_category === 'HOST'
    || asset.asset_category === 'MOBILE_DEVICE'
    || hasAgents;

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
            label: t('Assets'),
            link: '/admin/assets',
          },
          {
            label: asset.asset_name,
            current: true,
          },
        ]}
      />

      {/* Hero */}
      <DetailHero
        iconNode={<AssetCategoryIcon category={asset.asset_category} color="primary" />}
        title={asset.asset_name}
        action={(
          <>
            {/* Entity-scoped reports - self-hides without the reporting
                access capability. */}
            <EntityReportsPanel
              contextType="ENDPOINT"
              contextId={id}
              entityName={asset.asset_name}
            />
            <AssetPopover
              endpoint={asset as AssetPopoverProps['endpoint']}
              agentless={!hasAgents}
              onUpdate={() => loadAsset()}
              onDelete={() => navigate('/admin/assets')}
            />
          </>
        )}
        stats={(
          <>
            {supportsAgents && (
              <HeroStat icon={DevicesOtherOutlined} label={t('Agents')} value={asset.asset_agents?.length ?? 0} color={theme.palette.success.main} />
            )}
            <HeroStat icon={Binoculars} label={t('Findings')} value={findingsTotal ?? '-'} color={theme.palette.primary.main} />
            <HeroStat icon={TrackChangesOutlined} label={t('Injects played')} value={injectsTotal ?? '-'} color={theme.palette.warning.main} />
            <HeroStat icon={TrackChangesOutlined} label={t('Expectations tested')} value={posture.loading ? '-' : posture.tested} />
            <PostureScore
              scope="asset"
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
            <PostureScoreOverTimeChart scopeField="base_asset_side" entityId={id} height={280} />
          </SectionBlock>
          <SectionBlock title={t('Injects played over time')}>
            <InjectsPlayedOverTimeChart scopeField="base_assets_side" entityId={id} height={280} />
          </SectionBlock>
        </DetailSections>
      )}

      {currentTab === 'overview' && (
        <>
          {/* Information + the asset-kind-specific card side by side for a compact,
          grid-based overview (Network / Cloud / AI target as the second column). */}
          <DetailSections>
            <InformationGrid title={t('Asset Information')}>
              <Field label={t('Description')}>
                {asset.asset_description
                  ? <ExpandableMarkdown source={asset.asset_description} limit={300} />
                  : '-'}
              </Field>
              <Field label={t('Category')}>
                <Box sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                }}
                >
                  <AssetCategoryIcon category={asset.asset_category} fontSize="small" />
                  <span>
                    {asset.asset_category ? t(humanizeEnum(asset.asset_category)) : '-'}
                    {asset.asset_subcategory ? ` / ${t(humanizeEnum(asset.asset_subcategory))}` : ''}
                  </span>
                </Box>
              </Field>
              <Field label={t('Criticality')}>
                <ItemCriticality criticality={asset.asset_criticality} />
              </Field>
              <Field label={t('Internet-facing')}>
                <Typography variant="body2">{internetFacingLabel}</Typography>
              </Field>
              {asset.asset_url && (
                <Field label={t('URL')}><Typography variant="body2">{asset.asset_url}</Typography></Field>
              )}
              {asset.asset_linked_person && (
                <Field label={t('Person')}><Typography variant="body2">{emptyFilled(linkedPersonName)}</Typography></Field>
              )}
              <Field label={t('Tags')}>
                <ItemTags variant="list" tags={asset.asset_tags} />
              </Field>
            </InformationGrid>

            {isAiTarget && (
              <InformationGrid title={t('AI target connection')}>
                <Field label={t('Provider')}><Typography variant="body2">{emptyFilled(asset.ai_target_provider)}</Typography></Field>
                <Field label={t('Modality')}><Typography variant="body2">{emptyFilled(asset.ai_target_modality)}</Typography></Field>
                <Field label={t('Model')}><Typography variant="body2">{emptyFilled(asset.ai_target_model)}</Typography></Field>
                <Field label={t('Endpoint URL')}><Typography variant="body2">{emptyFilled(asset.ai_target_endpoint)}</Typography></Field>
                {asset.ai_target_system_prompt && (
                  <Field label={t('System prompt')}><ExpandableMarkdown source={asset.ai_target_system_prompt} limit={300} /></Field>
                )}
              </InformationGrid>
            )}

            {hasCloud && (
              <InformationGrid title={t('Cloud')}>
                <Field label={t('Cloud provider')}><Typography variant="body2">{asset.asset_cloud_provider ? t(humanizeEnum(asset.asset_cloud_provider)) : '-'}</Typography></Field>
                <Field label={t('Native type')}><Typography variant="body2">{emptyFilled(asset.asset_cloud_native_type)}</Typography></Field>
                <Field label={t('Region')}><Typography variant="body2">{emptyFilled(asset.asset_cloud_region)}</Typography></Field>
              </InformationGrid>
            )}

            {!isAiTarget && hasNetwork && (
              <InformationGrid title={t('Network')}>
                <Field label={t('Hostname')}><Typography variant="body2">{emptyFilled(asset.asset_hostname)}</Typography></Field>
                <Field label={t('Seen IP address')}><Typography variant="body2">{emptyFilled(asset.asset_seen_ip)}</Typography></Field>
                {asset.endpoint_platform && (
                  <Field label={t('Platform')}>
                    <Box sx={{
                      display: 'flex',
                      alignItems: 'center',
                    }}
                    >
                      <PlatformIcon platform={asset.endpoint_platform} width={20} marginRight={theme.spacing(2)} />
                  &nbsp;
                      {asset.endpoint_platform}
                    </Box>
                  </Field>
                )}
                {asset.endpoint_arch && (
                  <Field label={t('Architecture')}><EndpointArchFragment arch={asset.endpoint_arch} /></Field>
                )}
                {asset.asset_ips && asset.asset_ips.length > 0 && (
                  <Field label={t('IP Addresses')}>
                    <Box sx={{
                      maxHeight: theme.spacing(20),
                      overflowY: 'auto',
                    }}
                    >
                      {asset.asset_ips.map((ip: string) => (
                        <Typography key={ip} variant="body2" noWrap>{formatIp(ip)}</Typography>
                      ))}
                    </Box>
                  </Field>
                )}
                {asset.asset_mac_addresses && asset.asset_mac_addresses.length > 0 && (
                  <Field label={t('MAC Addresses')}>
                    <Box sx={{
                      maxHeight: theme.spacing(20),
                      overflowY: 'auto',
                    }}
                    >
                      {asset.asset_mac_addresses.map((mac: string) => (
                        <Typography key={mac} variant="body2" noWrap>{formatMacAddress(mac)}</Typography>
                      ))}
                    </Box>
                  </Field>
                )}
              </InformationGrid>
            )}
          </DetailSections>

          {hasAgents && (
            <SectionBlock title={t('Agents')}>
              <AgentList agents={asset.asset_agents} />
            </SectionBlock>
          )}

          <SectionBlock title={t('Findings')}>
            <FindingList
              filterLocalStorageKey="endpoint-findings"
              searchDistinctFindings={(input: SearchPaginationInput) => searchDistinctFindingsOnEndpoint(id, input)}
              contextId={id}
            />
          </SectionBlock>

          <SectionBlock title={t('Injects played')}>
            <InjectResultList
              fetchInjects={fetchInjectsPlayed}
              goTo={injectResultDetailPath}
              queryableHelpers={injectsHelpers}
              searchPaginationInput={injectsInput}
              contextId={id}
            />
          </SectionBlock>
        </>
      )}
    </Box>
  );
};

export default AssetDetail;
