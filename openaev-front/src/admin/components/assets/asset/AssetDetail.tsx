import { Alert, AlertTitle, Box, Chip, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { fetchAssetOverview } from '../../../../actions/assets/endpoint-actions';
import { searchAtomicTestings } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { searchDistinctFindingsOnEndpoint, searchFindingsOnEndpoint } from '../../../../actions/findings/finding-actions';
import { type UserHelper } from '../../../../actions/helper';
import { fetchPlayers } from '../../../../actions/users/User';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import EndpointArchFragment from '../../../../components/common/list/fragments/EndpointArchFragment';
import { generateFilterId } from '../../../../components/common/queryable/filter/FilterUtils';
import { type Page } from '../../../../components/common/queryable/Page';
import { initSorting } from '../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemCriticality from '../../../../components/ItemCriticality';
import ItemTags from '../../../../components/ItemTags';
import ItemTargets from '../../../../components/ItemTargets';
import Loader from '../../../../components/Loader';
import PlatformIcon from '../../../../components/PlatformIcon';
import { INJECT, SIMULATION } from '../../../../constants/Entities';
import { useHelper } from '../../../../store';
import {
  type AggregatedFindingOutput,
  type EndpointOverviewOutput,
  type Filter,
  type InjectResultOutput,
  type RelatedFindingOutput,
  type SearchPaginationInput,
  type TargetSimple,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { emptyFilled, formatIp, formatMacAddress } from '../../../../utils/String';
import InjectResultList from '../../atomic_testings/InjectResultList';
import FindingContextLink from '../../findings/FindingContextLink';
import FindingList from '../../findings/FindingList';
import { humanizeEnum } from '../asset-categories';
import AssetCategoryIcon from '../AssetCategoryIcon';
import AssetPopover, { type AssetPopoverProps } from '../endpoints/AssetPopover';
import AgentList from '../endpoints/endpoint/AgentList';

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

const SECTION_LABEL_SX = {
  fontFamily: '"Geologica", sans-serif',
  fontWeight: 600,
  fontSize: 11,
  letterSpacing: '0.12em',
  textTransform: 'uppercase' as const,
  color: 'text.secondary',
  marginBottom: 1.5,
};

// A single labelled field inside an information section.
const Field = ({ label, children }: {
  label: string;
  children: ReactNode;
}) => (
  <div>
    <Typography variant="h3" gutterBottom sx={{ fontSize: 12 }}>{label}</Typography>
    <div>{children}</div>
  </div>
);

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

  // Findings (works for any asset: the backend filters by the asset id in findings_assets).
  const additionalFilterNames = ['finding_inject_id', 'finding_simulation'];
  const additionalHeaders = useMemo(() => [
    {
      field: 'finding_inject',
      label: 'Inject',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={INJECT} />,
    },
    {
      field: 'finding_simulation',
      label: 'Simulation',
      isSortable: false,
      value: (finding: RelatedFindingOutput) => <FindingContextLink finding={finding} type={SIMULATION} />,
    },
    {
      field: 'finding_asset_groups',
      label: 'Asset groups',
      isSortable: false,
      value: (finding: AggregatedFindingOutput) => (
        <ItemTargets targets={(finding.finding_asset_groups || []).map(group => ({
          target_id: group.asset_group_id,
          target_name: group.asset_group_name,
          target_type: 'ASSETS_GROUPS',
        })) as TargetSimple[]}
        />
      ),
    },
  ], []);

  // Injects played: atomic tests that directly targeted this asset (inject_assets filter).
  const { queryableHelpers: injectsHelpers, searchPaginationInput: injectsInput } = useQueryableWithLocalStorage(
    'asset-injects',
    buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }),
  );
  const fetchInjectsPlayed = useCallback((input: SearchPaginationInput): Promise<{ data: Page<InjectResultOutput> }> => {
    const assetFilter: Filter = {
      id: generateFilterId(),
      key: 'inject_assets',
      mode: 'or',
      operator: 'contains',
      values: [id],
    };
    const scopedInput: SearchPaginationInput = {
      ...input,
      filterGroup: {
        mode: input.filterGroup?.mode ?? 'and',
        filters: [...(input.filterGroup?.filters ?? []), assetFilter],
      },
    };
    return searchAtomicTestings(scopedInput) as Promise<{ data: Page<InjectResultOutput> }>;
  }, [id]);

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
  const accent = theme.palette.primary.main;

  let internetFacingLabel = '-';
  if (asset.asset_internet_facing != null) {
    internetFacingLabel = asset.asset_internet_facing ? t('Yes') : t('No');
  }

  // Value-driven field visibility keeps each asset kind showing only its relevant data.
  const hasNetwork = !!(asset.asset_hostname || asset.asset_seen_ip || asset.endpoint_platform
    || asset.endpoint_arch || (asset.asset_ips && asset.asset_ips.length) || (asset.asset_mac_addresses && asset.asset_mac_addresses.length));
  const hasCloud = !!asset.asset_cloud_provider;
  const hasAgents = !!(asset.asset_agents && asset.asset_agents.length > 0);

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
            link: '/admin/assets/inventory',
          },
          {
            label: asset.asset_name,
            current: true,
          },
        ]}
      />

      {/* Hero */}
      <Paper
        variant="outlined"
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          padding: 2,
          borderRadius: 1,
          background: `linear-gradient(135deg, ${alpha(accent, 0.08)}, transparent 60%)`,
        }}
      >
        <Box
          sx={{
            width: 52,
            height: 52,
            borderRadius: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
            backgroundColor: alpha(accent, 0.12),
            border: `1px solid ${alpha(accent, 0.3)}`,
          }}
        >
          <AssetCategoryIcon category={asset.asset_category} color="primary" />
        </Box>
        <Box sx={{
          minWidth: 0,
          flex: 1,
        }}
        >
          <Tooltip title={asset.asset_name}>
            <Typography
              variant="h1"
              sx={{
                margin: 0,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {asset.asset_name}
            </Typography>
          </Tooltip>
          {hasAgents && (
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              marginTop: 0.5,
              flexWrap: 'wrap',
            }}
            >
              <Chip size="small" variant="outlined" label={`${asset.asset_agents.length} ${t('agent(s)')}`} sx={{ borderRadius: 1 }} />
            </Box>
          )}
        </Box>
        <AssetPopover
          endpoint={asset as AssetPopoverProps['endpoint']}
          agentless={!hasAgents}
          onUpdate={() => loadAsset()}
          onDelete={() => navigate('/admin/assets/inventory')}
        />
      </Paper>

      {/* Asset information */}
      <div>
        <Typography sx={SECTION_LABEL_SX}>{t('Asset Information')}</Typography>
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: 2,
          }}
        >
          <Field label={t('Description')}>
            <ExpandableMarkdown source={asset.asset_description} limit={300} />
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
        </Paper>
      </div>

      {/* AI target connection (AI targets only) */}
      {isAiTarget && (
        <div>
          <Typography sx={SECTION_LABEL_SX}>{t('AI target connection')}</Typography>
          <Paper
            variant="outlined"
            sx={{
              padding: 2,
              borderRadius: 1,
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: 2,
            }}
          >
            <Field label={t('Provider')}><Typography variant="body2">{emptyFilled(asset.ai_target_provider)}</Typography></Field>
            <Field label={t('Modality')}><Typography variant="body2">{emptyFilled(asset.ai_target_modality)}</Typography></Field>
            <Field label={t('Model')}><Typography variant="body2">{emptyFilled(asset.ai_target_model)}</Typography></Field>
            <Field label={t('Endpoint URL')}><Typography variant="body2">{emptyFilled(asset.ai_target_endpoint)}</Typography></Field>
            {asset.ai_target_system_prompt && (
              <Field label={t('System prompt')}><ExpandableMarkdown source={asset.ai_target_system_prompt} limit={300} /></Field>
            )}
          </Paper>
        </div>
      )}

      {/* Cloud (cloud/SaaS assets) */}
      {hasCloud && (
        <div>
          <Typography sx={SECTION_LABEL_SX}>{t('Cloud')}</Typography>
          <Paper
            variant="outlined"
            sx={{
              padding: 2,
              borderRadius: 1,
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: 2,
            }}
          >
            <Field label={t('Cloud provider')}><Typography variant="body2">{asset.asset_cloud_provider ? t(humanizeEnum(asset.asset_cloud_provider)) : '-'}</Typography></Field>
            <Field label={t('Native type')}><Typography variant="body2">{emptyFilled(asset.asset_cloud_native_type)}</Typography></Field>
            <Field label={t('Region')}><Typography variant="body2">{emptyFilled(asset.asset_cloud_region)}</Typography></Field>
          </Paper>
        </div>
      )}

      {/* Network (hosts / network / IoT / web / generic) */}
      {!isAiTarget && hasNetwork && (
        <div>
          <Typography sx={SECTION_LABEL_SX}>{t('Network')}</Typography>
          <Paper
            variant="outlined"
            sx={{
              padding: 2,
              borderRadius: 1,
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: 2,
            }}
          >
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
          </Paper>
        </div>
      )}

      {/* Agents (endpoints with agents) */}
      {hasAgents && (
        <div>
          <Typography sx={SECTION_LABEL_SX}>{t('Agents')}</Typography>
          <Paper variant="outlined" sx={{ borderRadius: 1 }}>
            <AgentList agents={asset.asset_agents} />
          </Paper>
        </div>
      )}

      {/* Findings */}
      <div>
        <Typography sx={SECTION_LABEL_SX}>{t('Findings')}</Typography>
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
          }}
        >
          <FindingList
            filterLocalStorageKey="endpoint-findings"
            searchDistinctFindings={(input: SearchPaginationInput) => searchDistinctFindingsOnEndpoint(id, input)}
            searchFindings={(input: SearchPaginationInput) => searchFindingsOnEndpoint(id, input)}
            additionalHeaders={additionalHeaders}
            additionalFilterNames={additionalFilterNames}
            contextId={id}
          />
        </Paper>
      </div>

      {/* Injects played */}
      <div>
        <Typography sx={SECTION_LABEL_SX}>{t('Injects played')}</Typography>
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
          }}
        >
          <InjectResultList
            fetchInjects={fetchInjectsPlayed}
            goTo={injectId => `/admin/atomic_testings/${injectId}`}
            queryableHelpers={injectsHelpers}
            searchPaginationInput={injectsInput}
            contextId={id}
          />
        </Paper>
      </div>
    </Box>
  );
};

export default AssetDetail;
