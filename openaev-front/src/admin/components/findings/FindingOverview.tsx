import { FormatListNumberedOutlined, LocationOnOutlined, ShieldOutlined } from '@mui/icons-material';
import { Box, Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router';

import {
  fetchStableFinding,
  fetchStableFindingSummary,
  searchStableFindingOccurrences,
} from '../../../actions/findings/finding-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { DetailHero, Field, HeroStat, InformationGrid, SectionBlock, SectionLabel } from '../../../components/common/detail/EntityDetailCommon';
import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import Loader from '../../../components/Loader';
import { INJECT } from '../../../constants/Entities';
import type {
  FindingOutput,
  RelatedFindingOutput,
  StableFindingSummaryOutput,
} from '../../../utils/api-types';
import { emptyFilled } from '../../../utils/String';
import AlsoDetectedOnPanel from './AlsoDetectedOnPanel';
import { getFindingAggregationCategory } from './findingAggregationCategories';
import FindingComments from './FindingComments';
import FindingContextLink from './FindingContextLink';
import FindingOccurrences from './FindingOccurrences';
import FindingTriageHistory from './FindingTriageHistory';
import getFindingTypeLabel from './FindingTypeLabel';
import FindingVulnerabilityPanel from './FindingVulnerabilityPanel';
import OCSFRemediationTab from './OCSFRemediationTab';

type FindingDetailOutput = Omit<FindingOutput, 'finding_type'> & {
  finding_type: FindingOutput['finding_type'] | 'ocsf';
  finding_legacy_id?: string;
  finding_source_finding_id?: string;
  finding_title?: string;
  finding_description?: string;
  finding_evidence?: string;
  finding_status_detail?: string;
  finding_resource_name?: string;
  finding_resource_type?: string;
  finding_resource_service?: string;
  finding_location_key?: string;
  finding_location_type?: string;
  finding_aggregation_category?: string;
  finding_risk_details?: string;
  finding_categories?: string[];
  finding_mitre_attack?: string[];
  finding_inject?: RelatedFindingOutput['finding_inject'];
  finding_source?: { injector_name?: string };
  finding_severity?: string;
  finding_resource?: string;
  finding_cloud_provider?: string;
  finding_cloud_account?: string;
  finding_cloud_region?: string;
  finding_compliance?: string;
  finding_remediation?: string;
  finding_raw_data?: string;
};

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

// Full-page finding overview: one stable tenant/source/type/value finding with its
// lifecycle summary (true first/last seen and occurrences), the
// vulnerability context when it is a CVE, and a tabbed lower section (occurrence
// timeline, sibling locations, raw scanner payload, triage/comment history) -
// mirroring the pre-rebuild FindingDetail.tsx tab organization users were used to.
const FindingOverview = () => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const { findingId } = useParams() as { findingId: string };

  const [finding, setFinding] = useState<FindingDetailOutput | null>(null);
  // Lifecycle dates and spread are computed from every occurrence of the stable Finding.
  const [summary, setSummary] = useState<StableFindingSummaryOutput | null>(null);
  const [cvssScore, setCvssScore] = useState<number | null>(null);

  useEffect(() => {
    fetchStableFinding(findingId).then(response => setFinding(response.data as FindingDetailOutput));
    fetchStableFindingSummary(findingId).then(response => setSummary(response.data));
  }, [findingId]);

  const typeLabel = useMemo(
    () => (finding ? getFindingTypeLabel(t, finding.finding_type, finding.finding_cloud_provider) : ''),
    [finding, t],
  );

  const isOCSF = finding?.finding_type === 'ocsf';
  const historyFindingId = finding?.finding_legacy_id ?? findingId;

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
  const displayedCvssScore = cvssScore;
  const aggregationCategory = getFindingAggregationCategory(finding.finding_aggregation_category);

  const renderTabPanel = () => {
    switch (currentTab) {
      case TAB_TIMELINE:
        return (
          <FindingOccurrences
            searchFindings={input => searchStableFindingOccurrences(findingId, input)}
            finding={finding}
            contextId={findingId}
          />
        );
      case TAB_ALSO_DETECTED_ON:
        return <AlsoDetectedOnPanel key={finding.finding_id} finding={finding} />;
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
              <FindingComments findingId={historyFindingId} />
            </Box>
            <Box>
              <SectionLabel>{t('Triage History')}</SectionLabel>
              <FindingTriageHistory findingId={historyFindingId} />
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
        chips={displayedCvssScore != null
          ? <Chip size="small" color="primary" variant="outlined" label={`CVSS ${displayedCvssScore.toFixed(1)}`} sx={{ borderRadius: 1 }} />
          : undefined}
        stats={(
          <>
            <HeroStat icon={FormatListNumberedOutlined} label={t('Occurrences')} value={summary?.finding_occurrences ?? '-'} />
            <HeroStat
              icon={LocationOnOutlined}
              label={t('Location')}
              value={summary?.finding_locations_count ?? '-'}
              color={theme.palette.primary.main}
            />
            {isCVE && (
              <HeroStat icon={ShieldOutlined} label={t('CVSS score')} value={displayedCvssScore != null ? displayedCvssScore.toFixed(1) : '-'} color={theme.palette.warning.main} />
            )}
          </>
        )}
      />

      <InformationGrid title={t('Information')}>
        <Field label={t('Type')}>{typeLabel}</Field>
        <Field label={t('Category')}>{t(aggregationCategory.label)}</Field>
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
        {finding.finding_inject && (
          <Field label={t('Inject')}>
            <FindingContextLink finding={finding} type={INJECT} />
          </Field>
        )}
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
            <Field label={t('Severity')}>{emptyFilled(finding.finding_severity)}</Field>
            <Field label={t('Rule ID')}>{finding.finding_value}</Field>
            <Field label={t('Resource')}>{finding.finding_resource_name ?? emptyFilled(finding.finding_resource)}</Field>
            <Field label={t('Resource UID')}>{emptyFilled(finding.finding_resource)}</Field>
            <Field label={t('Resource type')}>{emptyFilled(finding.finding_resource_type)}</Field>
            <Field label={t('Service')}>{emptyFilled(finding.finding_resource_service)}</Field>
            <Field label={t('Cloud provider')}>{emptyFilled(finding.finding_cloud_provider)}</Field>
            <Field label={t('Cloud account')}>{emptyFilled(finding.finding_cloud_account)}</Field>
            <Field label={t('Cloud region')}>{emptyFilled(finding.finding_cloud_region)}</Field>
            <Field label={t('Compliance')}>{emptyFilled(finding.finding_compliance)}</Field>
          </InformationGrid>
          <InformationGrid title={t('Evidence')}>
            <Field label={t('Description')}>{emptyFilled(finding.finding_description)}</Field>
            <Field label={t('Evidence')}>{emptyFilled(finding.finding_evidence)}</Field>
            <Field label={t('Status detail')}>
              <Typography
                component="code"
                sx={{
                  fontFamily: 'Consolas, monaco, monospace',
                  fontSize: 12.5,
                }}
              >
                {emptyFilled(finding.finding_status_detail)}
              </Typography>
            </Field>
            <Field label={t('Source finding ID')}>{emptyFilled(finding.finding_source_finding_id)}</Field>
          </InformationGrid>
          <InformationGrid title={t('Attack context')}>
            <Field label={t('Risk details')}>{emptyFilled(finding.finding_risk_details)}</Field>
            <Field label={t('Categories')}>
              <Box sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 0.5,
              }}
              >
                {(finding.finding_categories ?? []).map(category => (
                  <Chip key={category} size="small" variant="outlined" label={category} />
                ))}
              </Box>
            </Field>
            <Field label={t('MITRE ATT&CK')}>
              <Box sx={{
                display: 'flex',
                flexDirection: 'column',
                gap: 0.5,
              }}
              >
                {(finding.finding_mitre_attack ?? []).map(technique => (
                  <Typography key={technique} variant="body2">{technique}</Typography>
                ))}
              </Box>
            </Field>
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
