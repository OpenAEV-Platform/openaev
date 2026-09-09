import { DevicesOutlined, FormatListNumberedOutlined, Groups3Outlined, PersonOutlined, ShieldOutlined } from '@mui/icons-material';
import { Box, Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import { fetchFinding, fetchFindingSummary, searchFindings } from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { DetailHero, Field, HeroStat, InformationGrid, SectionBlock, SectionLabel } from '../../../components/common/detail/EntityDetailCommon';
import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import Loader from '../../../components/Loader';
import type { FindingOutput, FindingSummaryOutput } from '../../../utils/api-types';
import { emptyFilled } from '../../../utils/String';
import AlsoDetectedOnPanel from './AlsoDetectedOnPanel';
import FindingComments from './FindingComments';
import FindingOccurrences from './FindingOccurrences';
import FindingTriageHistory from './FindingTriageHistory';
import getFindingTypeLabel from './FindingTypeLabel';
import FindingVulnerabilityPanel from './FindingVulnerabilityPanel';
import OCSFRemediationTab from './OCSFRemediationTab';

// finding_raw_data is stored as a compact single-line JSON string (see
// OCSFOutputProcessor#enrichFinding); pretty-print it for readability, falling back to the raw
// text verbatim if it somehow isn't valid JSON rather than hiding it.
const formatRawData = (rawData: string): string => {
  try {
    return JSON.stringify(JSON.parse(rawData), null, 2);
  } catch {
    return rawData;
  }
};

const TAB_TIMELINE = 'Timeline';
const TAB_ALSO_DETECTED_ON = 'Also Detected On';
const TAB_RAW_RESPONSE = 'Raw response';
const TAB_HISTORY = 'History';

// Full-page finding overview: one deduplicated finding (type + value) with its
// group-wide summary (true first/last seen, occurrences, impact spread), the
// vulnerability context when it is a CVE, and a tabbed lower section (occurrence
// timeline, cross-asset relations, raw scanner payload, triage/comment history) -
// mirroring the pre-rebuild FindingDetail.tsx tab organization users were used to.
const FindingOverview = () => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const { findingId } = useParams() as { findingId: string };

  const [finding, setFinding] = useState<FindingOutput | null>(null);
  // Group-wide aggregates are computed server-side: a Finding row is ONE
  // occurrence (per inject), so its own dates/links cannot answer "since when
  // and how widely has this been seen?".
  const [summary, setSummary] = useState<FindingSummaryOutput | null>(null);
  const [cvssScore, setCvssScore] = useState<number | null>(null);

  useEffect(() => {
    fetchFinding(findingId).then(response => setFinding(response.data as FindingOutput));
    fetchFindingSummary(findingId).then(response => setSummary(response.data as FindingSummaryOutput));
  }, [findingId]);

  const typeLabel = useMemo(
    () => (finding ? getFindingTypeLabel(t, finding.finding_type, finding.finding_cloud_provider) : ''),
    [finding, t],
  );

  const isOCSF = finding?.finding_type === 'ocsf';

  // Raw response only exists for OCSF/Prowler findings (Finding#rawData is populated solely by
  // OCSFOutputProcessor) - every other finding type never shows that tab at all.
  const tabEntries: TabsEntry[] = useMemo(() => {
    const entries: TabsEntry[] = [
      {
        key: TAB_TIMELINE,
        label: t('Timeline'),
      },
      {
        key: TAB_ALSO_DETECTED_ON,
        label: t('Also Detected On'),
      },
    ];
    if (isOCSF) {
      entries.push({
        key: TAB_RAW_RESPONSE,
        label: t('Raw response'),
      });
    }
    entries.push({
      key: TAB_HISTORY,
      label: t('History'),
    });
    return entries;
  }, [isOCSF, t]);

  const { currentTab, handleChangeTab } = useTabs(TAB_TIMELINE);

  if (!finding) {
    return <Loader />;
  }

  const isCVE = finding.finding_type === 'cve';

  const renderTabPanel = () => {
    switch (currentTab) {
      case TAB_TIMELINE:
        return (
          <FindingOccurrences
            searchFindings={searchFindings}
            finding={finding}
            contextId={findingId}
          />
        );
      case TAB_ALSO_DETECTED_ON:
        return <AlsoDetectedOnPanel finding={finding} />;
      case TAB_RAW_RESPONSE:
        return finding.finding_raw_data
          ? (
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
                  maxHeight: 480,
                  overflow: 'auto',
                }}
              >
                {formatRawData(finding.finding_raw_data)}
              </Box>
            )
          : (
              <Box sx={{ color: 'text.secondary' }}>
                {t('There is no raw response available for this finding.')}
              </Box>
            );
      case TAB_HISTORY:
        // Comments and triage history are two distinct read/write logs on the same finding -
        // combined under one "History" tab rather than two separate tabs, per user request.
        return (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 3,
          }}
          >
            <Box>
              <SectionLabel>{t('Comments')}</SectionLabel>
              <FindingComments findingId={findingId} />
            </Box>
            <Box>
              <SectionLabel>{t('Triage History')}</SectionLabel>
              <FindingTriageHistory findingId={findingId} />
            </Box>
          </Box>
        );
      default:
        return null;
    }
  };

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
        <Field label={t('Source')}>{finding.finding_source?.injector_name ?? t('Manual')}</Field>
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

      {/* OCSF/Prowler cloud context: resource identifier, account/region/provider and
          the violated compliance requirements, plus a dedicated remediation reading pane. */}
      {isOCSF && (
        <>
          <InformationGrid title={t('Cloud details')}>
            <Field label={t('Resource')}>{emptyFilled(finding.finding_resource)}</Field>
            <Field label={t('Cloud provider')}>{emptyFilled(finding.finding_cloud_provider)}</Field>
            <Field label={t('Cloud account')}>{emptyFilled(finding.finding_cloud_account)}</Field>
            <Field label={t('Cloud region')}>{emptyFilled(finding.finding_cloud_region)}</Field>
            <Field label={t('Compliance')}>{emptyFilled(finding.finding_compliance)}</Field>
          </InformationGrid>
          <div style={{ marginTop: theme.spacing(1) }}>
            <SectionBlock title={t('Remediation')}>
              <OCSFRemediationTab remediation={finding.finding_remediation} />
            </SectionBlock>
          </div>
        </>
      )}

      {/* Lower section as tabs (mirrors the pre-rebuild FindingDetail.tsx organization):
          Timeline (occurrences) is the default/first tab, followed by Also Detected On,
          the raw scanner payload for OCSF findings only, and the combined comments +
          triage history log. */}
      <Box sx={{ marginTop: theme.spacing(1) }}>
        <Tabs
          entries={tabEntries}
          currentTab={currentTab}
          onChange={handleChangeTab}
        />
        <Box sx={{ marginTop: 2 }}>
          {renderTabPanel()}
        </Box>
      </Box>
    </Box>
  );
};

export default FindingOverview;
