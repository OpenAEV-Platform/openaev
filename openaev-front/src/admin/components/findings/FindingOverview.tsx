import { DevicesOutlined, FormatListNumberedOutlined, Groups3Outlined, PersonOutlined, ShieldOutlined } from '@mui/icons-material';
import { Box, Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchFinding, fetchFindingSummary, searchFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { DetailHero, Field, HeroStat, InformationGrid } from '../../../components/common/detail/EntityDetailCommon';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import Loader from '../../../components/Loader';
import type { Finding, FindingSummaryOutput } from '../../../utils/api-types';
import { emptyFilled } from '../../../utils/String';
import ContractOutputElementType from './ContractOutputElementType';
import FindingOccurrences from './FindingOccurrences';
import FindingVulnerabilityPanel from './FindingVulnerabilityPanel';

// Full-page finding overview: one deduplicated finding (type + value) with its
// group-wide summary (true first/last seen, occurrences, impact spread), the
// vulnerability context when it is a CVE, and the occurrence timeline.
const FindingOverview = () => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const { findingId } = useParams() as { findingId: string };

  const [finding, setFinding] = useState<Finding | null>(null);
  // Group-wide aggregates are computed server-side: a Finding row is ONE
  // occurrence (per inject), so its own dates/links cannot answer "since when
  // and how widely has this been seen?".
  const [summary, setSummary] = useState<FindingSummaryOutput | null>(null);
  const [cvssScore, setCvssScore] = useState<number | null>(null);

  useEffect(() => {
    fetchFinding(findingId).then(response => setFinding(response.data as Finding));
    fetchFindingSummary(findingId).then(response => setSummary(response.data as FindingSummaryOutput));
  }, [findingId]);

  const typeLabel = useMemo(
    () => (finding ? t(ContractOutputElementType[finding.finding_type] ?? finding.finding_type) : ''),
    [finding, t],
  );

  if (!finding) {
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
            <HeroStat icon={FormatListNumberedOutlined} label={t('Occurrences')} value={summary?.finding_occurrences ?? '-'} />
            <HeroStat icon={DevicesOutlined} label={t('Impacted assets')} value={summary?.finding_assets_count ?? '-'} color={theme.palette.primary.main} />
            {/* Team / person spread only shows when the finding actually touched people
                (e.g. phishing credentials): machine findings keep a compact stat row. */}
            {(summary?.finding_teams_count ?? 0) > 0 && (
              <HeroStat icon={Groups3Outlined} label={t('Impacted teams')} value={summary?.finding_teams_count ?? '-'} color={theme.palette.success.main} />
            )}
            {(summary?.finding_users_count ?? 0) > 0 && (
              <HeroStat icon={PersonOutlined} label={t('Impacted persons')} value={summary?.finding_users_count ?? '-'} color={theme.palette.success.main} />
            )}
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
        {/* Group-wide dates from the summary: the fetched row's own dates only
            cover one occurrence and would understate the group (the historical
            "first seen shows a later date" bug). */}
        <Field label={t('First seen')}>{summary ? fldt(summary.finding_first_seen) : '-'}</Field>
        <Field label={t('Last seen')}>{summary ? fldt(summary.finding_last_seen) : '-'}</Field>
        <Field label={t('Tags')}>
          <ItemTags variant="list" tags={finding.finding_tags ?? []} />
        </Field>
      </InformationGrid>

      {/* CVE context: everything known about the vulnerability (identity,
          description, remediation, weaknesses, references) in ONE paper. */}
      {isCVE && (
        <FindingVulnerabilityPanel
          cveId={finding.finding_value}
          onCvssScore={setCvssScore}
        />
      )}

      {/* Occurrence timeline: one entry per inject that produced this finding,
          as a table or a horizontal time strip. */}
      <div style={{ marginTop: theme.spacing(1) }}>
        <FindingOccurrences
          searchFindings={searchFindings}
          finding={finding}
          contextId={findingId}
        />
      </div>
    </Box>
  );
};

export default FindingOverview;
