import { DevicesOutlined, FormatListNumberedOutlined, ShieldOutlined } from '@mui/icons-material';
import { Box, Chip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchFinding, searchFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { Field, HeroStat, HeroStats, InformationGrid, SectionBlock } from '../../../components/common/detail/EntityDetailCommon';
import { buildFilter } from '../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import ExpandableMarkdown from '../../../components/ExpandableMarkdown';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import Loader from '../../../components/Loader';
import { INJECT, SCENARIO, SIMULATION } from '../../../constants/Entities';
import type { AggregatedFindingOutput, Finding, RelatedFindingOutput } from '../../../utils/api-types';
import { emptyFilled } from '../../../utils/String';
import ContractOutputElementType from './ContractOutputElementType';
import FindingContextLink from './FindingContextLink';
import FindingDetail from './FindingDetail';

// Full-page finding overview: replaces the former drawer so every finding
// exposes all of its metadata and full pivots (endpoints, injects,
// simulations, scenarios and - for CVEs - the vulnerability + remediation).
const FindingOverview = () => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  const { findingId } = useParams() as { findingId: string };

  const [finding, setFinding] = useState<Finding | null>(null);
  const [cvssScore, setCvssScore] = useState<number | null>(null);
  const [occurrences, setOccurrences] = useState<number | null>(null);
  const [endpointCount, setEndpointCount] = useState<number | null>(null);

  useEffect(() => {
    fetchFinding(findingId).then(response => setFinding(response.data as Finding));
  }, [findingId]);

  const typeLabel = useMemo(
    () => (finding ? t(ContractOutputElementType[finding.finding_type] ?? finding.finding_type) : ''),
    [finding, t],
  );

  // Aggregate view expected by FindingDetail / RelatedInjectsTab (keyed on
  // type + value, not on a single occurrence id).
  const aggregated: AggregatedFindingOutput | null = useMemo(() => (finding
    ? {
        finding_id: finding.finding_id,
        finding_type: finding.finding_type,
        finding_value: finding.finding_value,
        finding_assets: [],
        finding_created_at: finding.finding_created_at,
      }
    : null), [finding]);

  // Count occurrences and impacted endpoints across the whole platform. The
  // endpoint count needs every occurrence's assets, so page through the
  // occurrences (bounded, to stay cheap on pathological findings) and dedupe.
  useEffect(() => {
    if (!finding) return undefined;
    let cancelled = false;
    const pageSize = 500;
    const maxPages = 10;
    const baseFilters = {
      mode: 'and' as const,
      filters: [
        buildFilter('finding_value', [finding.finding_value], 'eq'),
        // The values must match the backend enum names (EnumType.STRING
        // column), which the display mapping mirrors - not the raw JSON keys.
        buildFilter('finding_type', [ContractOutputElementType[finding.finding_type] ?? finding.finding_type], 'eq'),
      ],
    };
    const fetchPage = (page: number) => searchFindings(buildSearchPagination({
      page,
      size: pageSize,
      filterGroup: baseFilters,
    })) as Promise<{
      data: {
        totalElements?: number;
        totalPages?: number;
        content?: RelatedFindingOutput[];
      };
    }>;
    (async () => {
      try {
        const ids = new Set<string>();
        const collect = (rows?: RelatedFindingOutput[]) =>
          (rows ?? []).forEach(row => (row.finding_assets ?? []).forEach(asset => ids.add(asset.asset_id)));
        const first = await fetchPage(0);
        if (!cancelled) setOccurrences(first.data.totalElements ?? 0);
        collect(first.data.content);
        const totalPages = Math.min(first.data.totalPages ?? 1, maxPages);
        const rest = await Promise.all(Array.from({ length: Math.max(totalPages - 1, 0) }, (_, index) => fetchPage(index + 1)));
        rest.forEach(page => collect(page.data.content));
        if (!cancelled) setEndpointCount(ids.size);
      } catch {
        if (!cancelled) {
          setOccurrences(0);
          setEndpointCount(null);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [finding]);

  const additionalHeaders = useMemo(() => [
    {
      field: 'finding_inject',
      label: 'Inject',
      isSortable: false,
      value: (row: RelatedFindingOutput) => <FindingContextLink finding={row} type={INJECT} />,
    },
    {
      field: 'finding_simulation',
      label: 'Simulation',
      isSortable: false,
      value: (row: RelatedFindingOutput) => <FindingContextLink finding={row} type={SIMULATION} />,
    },
    {
      field: 'finding_scenario',
      label: 'Scenario',
      isSortable: false,
      value: (row: RelatedFindingOutput) => <FindingContextLink finding={row} type={SCENARIO} />,
    },
  ], []);

  const additionalFilterNames = ['finding_inject_id', 'finding_simulation', 'finding_scenario'];

  if (!finding || !aggregated) {
    return <Loader />;
  }

  const isCVE = finding.finding_type === 'cve';

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
            label: t('Findings'),
            link: '/admin/findings',
          },
          {
            label: finding.finding_value,
            current: true,
          },
        ]}
      />

      {/* Hero: framed finding-type icon + type overline + value + CVSS chip,
          with tiny headline stats on a second row (matching scenario/simulation). */}
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          padding: 2,
          borderRadius: 1,
          border: `1px solid ${theme.palette.divider}`,
          background: `linear-gradient(135deg, ${alpha(accent, 0.08)}, transparent 60%)`,
        }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
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
            <FindingIcon findingType={finding.finding_type} />
          </Box>
          <Box sx={{
            minWidth: 0,
            flex: 1,
          }}
          >
            <Box sx={{
              fontFamily: '"Geologica", sans-serif',
              fontWeight: 600,
              fontSize: 11,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              color: 'text.secondary',
            }}
            >
              {typeLabel}
            </Box>
            <Box
              sx={{
                fontSize: 20,
                fontWeight: 600,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
              title={finding.finding_value}
            >
              {finding.finding_value}
            </Box>
          </Box>
          {cvssScore != null && (
            <Chip color="primary" variant="outlined" label={`CVSS ${cvssScore.toFixed(1)}`} sx={{ borderRadius: 1 }} />
          )}
        </Box>
        <HeroStats>
          <HeroStat icon={FormatListNumberedOutlined} label={t('Occurrences')} value={occurrences ?? '-'} />
          <HeroStat icon={DevicesOutlined} label={t('Impacted endpoints')} value={endpointCount ?? '-'} color={theme.palette.primary.main} />
          {isCVE && (
            <HeroStat icon={ShieldOutlined} label={t('CVSS score')} value={cvssScore != null ? cvssScore.toFixed(1) : '-'} color={theme.palette.warning.main} />
          )}
        </HeroStats>
      </Box>

      <InformationGrid title={t('Information')}>
        <Field label={t('Type')}>{typeLabel}</Field>
        <Field label={t('Value')}>
          <ExpandableMarkdown source={finding.finding_value} limit={300} />
        </Field>
        <Field label={t('Field')}>{emptyFilled(finding.finding_field)}</Field>
        <Field label={t('First seen')}>{fldt(finding.finding_created_at)}</Field>
        <Field label={t('Last seen')}>{fldt(finding.finding_updated_at)}</Field>
        <Field label={t('Tags')}>
          <ItemTags variant="list" tags={finding.finding_tags ?? []} />
        </Field>
      </InformationGrid>

      <SectionBlock title={t('Affected endpoints & context')} disablePadding>
        <FindingDetail
          searchFindings={searchFindings}
          selectedFinding={aggregated}
          additionalHeaders={additionalHeaders}
          additionalFilterNames={additionalFilterNames}
          contextId={findingId}
          onCvssScore={setCvssScore}
        />
      </SectionBlock>
    </Box>
  );
};

export default FindingOverview;
