import { Chip } from '@filigran/design-system';
import { FormatListNumberedOutlined, ShieldOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchFinding } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { DetailHero, Field, HeroStat, InformationGrid } from '../../../components/common/detail/EntityDetailCommon';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import ItemTargets from '../../../components/ItemTargets';
import Loader from '../../../components/Loader';
import XtmCounterpartsEmblem from '../../../components/xtm/XtmCounterpartsEmblem';
import type { FindingOutput } from '../../../utils/api-types';
import useExplorationGraph from '../../../utils/hooks/useExplorationGraph';
import ExploreInGraphButton from '../graph/ExploreInGraphButton';
import ContractOutputElementType from './ContractOutputElementType';
import FindingOccurrences from './FindingOccurrences';
import FindingVulnerabilityPanel from './FindingVulnerabilityPanel';

const FindingOverview = () => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const { findingId } = useParams() as { findingId: string };
  // The hero actions and the OpenCTI / OpenCRQ cards belong to the exploration graph preview
  // feature (off by default); off, the page is the one shipped before it.
  const graphEnabled = useExplorationGraph();

  const [finding, setFinding] = useState<FindingOutput | null>(null);
  const [cvssScore, setCvssScore] = useState<number | null>(null);

  useEffect(() => {
    fetchFinding(findingId).then(response => setFinding(response.data as FindingOutput));
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
        action={(isCVE || graphEnabled) ? (
          <>
            {/* The single XTM One emblem first (its own XTM_COUNTERPARTS flag), then the graph. */}
            {isCVE && <XtmCounterpartsEmblem kind="vulnerabilities" objectKey={finding.finding_value} />}
            {graphEnabled && <ExploreInGraphButton type="FINDING" entityId={findingId} />}
          </>
        ) : undefined}
        chips={cvssScore != null
          ? <Chip label={`CVSS ${cvssScore.toFixed(1)}`} severity="info" />
          : undefined}
        stats={(
          <>
            <HeroStat icon={FormatListNumberedOutlined} label={t('Occurrences')} value={finding.finding_occurrences_count ?? '-'} />
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
        <Field label={t('Target')}>
          {finding.finding_location ? <ItemTargets targets={[finding.finding_location]} /> : '-'}
        </Field>
        <Field label={t('First seen')}>{fldt(finding.finding_first_seen)}</Field>
        <Field label={t('Last seen')}>{fldt(finding.finding_last_seen)}</Field>
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
          findingId={findingId}
          findingType={finding.finding_type}
          contextId={findingId}
        />
      </div>
    </Box>
  );
};

export default FindingOverview;
