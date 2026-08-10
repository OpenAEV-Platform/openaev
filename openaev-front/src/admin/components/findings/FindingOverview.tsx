import { DevicesOutlined, FormatListNumberedOutlined, ShieldOutlined } from '@mui/icons-material';
import { Box, Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchFinding, searchFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { DetailHero, Field, HeroStat, InformationGrid, SectionLabel } from '../../../components/common/detail/EntityDetailCommon';
import { buildFilter } from '../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
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
// exposes all of its metadata and full pivots (assets, injects,
// simulations, scenarios and - for CVEs - the vulnerability + remediation).
const FindingOverview = () => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const { findingId } = useParams() as { findingId: string };

  const [finding, setFinding] = useState<Finding | null>(null);
  const [cvssScore, setCvssScore] = useState<number | null>(null);
  const [occurrences, setOccurrences] = useState<number | null>(null);
  const [assetCount, setAssetCount] = useState<number | null>(null);

  useEffect(() => {
    fetchFinding(findingId).then(response => setFinding(response.data as Finding));
  }, [findingId]);

  const typeLabel = useMemo(
    () => (finding ? t(ContractOutputElementType[finding.finding_type] ?? finding.finding_type) : ''),
    [finding, t],
  );

  // Aggregate view expected by FindingDetail / RelatedInjectsTab (keyed on
  // type + value, not on a single occurrence id). Triage status is not shown/edited on this
  // page (see the Information grid below): it only belongs to the findings list, since this
  // page is a list of occurrences of the same check and only the most recent one is exposed
  // here - so a fixed placeholder is enough to satisfy the (list-oriented) type contract.
  const aggregated: AggregatedFindingOutput | null = useMemo(() => (finding
    ? {
        finding_id: finding.finding_id,
        finding_type: finding.finding_type,
        finding_value: finding.finding_value,
        finding_assets: [],
        finding_created_at: finding.finding_created_at,
        finding_updated_at: finding.finding_updated_at,
        finding_remediation: finding.finding_remediation,
        finding_triage_status: 'UNTRIAGED',
      }
    : null), [finding]);

  // Count occurrences and impacted assets across the whole platform. The
  // asset count needs every occurrence's assets, so page through the
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
        if (!cancelled) setAssetCount(ids.size);
      } catch {
        if (!cancelled) {
          setOccurrences(0);
          setAssetCount(null);
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
  const isOCSF = finding.finding_type === 'ocsf';

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

      {/* Hero: shared DetailHero with the finding type as overline and the
          CVSS chip in the standard chips row (matching every other detail page). */}
      <DetailHero
        iconNode={<FindingIcon findingType={finding.finding_type} />}
        overline={typeLabel}
        title={finding.finding_value}
        chips={cvssScore != null
          ? <Chip size="small" color="primary" variant="outlined" label={`CVSS ${cvssScore.toFixed(1)}`} sx={{ borderRadius: 1 }} />
          : undefined}
        stats={(
          <>
            <HeroStat icon={FormatListNumberedOutlined} label={t('Occurrences')} value={occurrences ?? '-'} />
            <HeroStat icon={DevicesOutlined} label={t('Impacted assets')} value={assetCount ?? '-'} color={theme.palette.primary.main} />
            {isCVE && (
              <HeroStat icon={ShieldOutlined} label={t('CVSS score')} value={cvssScore != null ? cvssScore.toFixed(1) : '-'} color={theme.palette.warning.main} />
            )}
          </>
        )}
      />

      <InformationGrid title={t('Information')}>
        <Field label={t('Type')}>{typeLabel}</Field>
        <Field label={t('Value')}>
          <Box
            component="pre"
            sx={{
              margin: 0,
              padding: theme.spacing(1, 1.5),
              borderRadius: 1,
              backgroundColor: theme.palette.background.accent,
              border: `1px solid ${theme.palette.divider}`,
              fontFamily: 'Consolas, monaco, monospace',
              fontSize: 12.5,
              lineHeight: 1.5,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              color: theme.palette.text.primary,
            }}
          >
            {finding.finding_value}
          </Box>
        </Field>
        <Field label={t('Field')}>{emptyFilled(finding.finding_field)}</Field>
        <Field label={t('Source')}>{finding.finding_source?.injector_name ?? t('Manual')}</Field>
        <Field label={t('First seen')}>{fldt(finding.finding_created_at)}</Field>
        <Field label={t('Last seen')}>{fldt(finding.finding_updated_at)}</Field>
        <Field label={t('Tags')}>
          <ItemTags variant="list" tags={finding.finding_tags ?? []} />
        </Field>
      </InformationGrid>

      {/* OCSF/Prowler-specific misconfiguration data - only meaningful for cloud findings,
          so the section is hidden entirely for every other finding type rather than showing
          empty fields. */}
      {isOCSF && (
        <InformationGrid title={t('Cloud details')}>
          <Field label={t('Severity')}>{emptyFilled(finding.finding_severity)}</Field>
          <Field label={t('Resource')}>{emptyFilled(finding.finding_resource)}</Field>
          <Field label={t('Cloud account')}>{emptyFilled(finding.finding_cloud_account)}</Field>
          <Field label={t('Region')}>{emptyFilled(finding.finding_cloud_region)}</Field>
          <Field label={t('Compliance')}>{emptyFilled(finding.finding_compliance)}</Field>
        </InformationGrid>
      )}

      {/* Flat list (no surrounding Paper): the section label sits directly above
          the related-reports list, matching OpenCTI's plain list sections. The
          extra top margin gives this section a clear break from the Information
          card above (the shared page gap alone reads as too tight here). */}
      <div style={{ marginTop: theme.spacing(1) }}>
        <SectionLabel>{t('Affected assets & context')}</SectionLabel>
        <FindingDetail
          searchFindings={searchFindings}
          selectedFinding={aggregated}
          additionalHeaders={additionalHeaders}
          additionalFilterNames={additionalFilterNames}
          contextId={findingId}
          onCvssScore={setCvssScore}
        />
      </div>
    </Box>
  );
};

export default FindingOverview;
