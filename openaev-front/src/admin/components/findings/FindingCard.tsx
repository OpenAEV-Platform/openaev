import { Chip, type ChipSeverity, Text } from '@filigran/design-system';
import { type SvgIconProps } from '@mui/material';
import { FormatListNumbered, MonitorCellphone, ShieldOutline } from 'mdi-material-ui';
import { type ComponentType, type FunctionComponent, useEffect, useState } from 'react';

import { fetchFindingSummary } from '../../../actions/findings/finding-actions';
import { fetchVulnerabilityByExternalId } from '../../../actions/vulnerability-actions';
import EntityCard from '../../../components/common/card/EntityCard';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput, type FindingSummaryOutput, type VulnerabilityOutput } from '../../../utils/api-types';
import { getSeverityAndColor, type SeverityAndColor } from '../../../utils/Colors';
import ContractOutputElementType from './ContractOutputElementType';

// CVSS v3.1 qualitative rating -> chip tone, as in the Figma footer
// (low green, medium amber, high red).
const CRITICALITY_SEVERITY: Record<SeverityAndColor['severity'], ChipSeverity> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'medium',
  LOW: 'low',
  NONE: 'neutral',
};

const CRITICALITY_LABELS: Record<SeverityAndColor['severity'], string> = {
  CRITICAL: 'Critical',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
  NONE: 'None',
};

interface InfoPillProps {
  icon: ComponentType<SvgIconProps>;
  value: string | number;
  label: string;
  iconColor?: string;
}

// The framed "icon + count + label" figures of the Figma card content
// (Design_System_2026, node 8745:64999): 4px padding, 8px between the parts.
// The glyphs are the MDI twins of the Figma ones (Lucide list-ordered,
// monitor-smartphone, shield).
const InfoPill = ({ icon: Icon, value, label, iconColor }: InfoPillProps) => (
  <div
    className="border border-elevation-subtle-soft"
    style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 8,
      padding: 4,
      borderRadius: 4,
      minWidth: 0,
    }}
  >
    <Icon
      style={iconColor ? {
        color: iconColor,
        flexShrink: 0,
      } : { flexShrink: 0 }}
      className={iconColor ? undefined : 'text-filigran-brand-primary'}
      sx={{ fontSize: 16 }}
    />
    <Text variant="content-compact-bold" className="text-default-primary" style={{ flexShrink: 0 }}>{value}</Text>
    <Text variant="content-compact" className="text-default-secondary truncate">{label}</Text>
  </div>
);

interface Props { finding: AggregatedFindingOutput }

const FindingCard: FunctionComponent<Props> = ({ finding }) => {
  const { t } = useFormatter();
  const isCve = finding.finding_type === 'cve';
  const [cvssScore, setCvssScore] = useState<number | null>(null);
  const [occurrences, setOccurrences] = useState<number | null>(null);

  // The aggregated list carries no occurrences (one per inject that produced
  // the value): read them from the group summary, as the finding page does.
  useEffect(() => {
    let active = true;
    fetchFindingSummary(finding.finding_id, false)
      .then((res: { data: FindingSummaryOutput }) => {
        if (active) {
          setOccurrences(res.data.finding_occurrences ?? null);
        }
      })
      .catch(() => {
        if (active) {
          setOccurrences(null);
        }
      });
    return () => {
      active = false;
    };
  }, [finding.finding_id]);

  // The aggregated list carries no CVSS: read it from the vulnerability of the
  // CVE. An unknown CVE (404) just leaves the card without score nor criticality.
  useEffect(() => {
    if (!isCve) {
      return undefined;
    }
    let active = true;
    fetchVulnerabilityByExternalId(finding.finding_value, false)
      .then((res: { data: VulnerabilityOutput | null }) => {
        if (active) {
          setCvssScore(res.data?.vulnerability_cvss_v31 ?? null);
        }
      })
      .catch(() => {
        if (active) {
          setCvssScore(null);
        }
      });
    return () => {
      active = false;
    };
  }, [isCve, finding.finding_value]);

  const cvss = cvssScore != null ? getSeverityAndColor(cvssScore) : null;
  const assetsCount = finding.finding_assets?.length ?? 0;

  return (
    // DS 2026 layout (Figma Design_System_2026, node 8745:64999): finding type
    // and value, the CVSS and criticality chips of a CVE, then the figures at
    // the bottom. No footer band. The height fits the content;
    // the grid then gives every card of the page the tallest one's height.
    <EntityCard
      testId="finding-card"
      layout="ds2026"
      ds2026Height="auto"
      to={`/admin/findings/${finding.finding_id}`}
      footer={false}
      icon={<FindingIcon findingType={finding.finding_type} />}
      overline={t(ContractOutputElementType[finding.finding_type] ?? finding.finding_type)}
      title={finding.finding_value}
      tags={cvss && (
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 8,
        }}
        >
          <Chip label={`CVSS ${cvssScore?.toFixed(1)}`} severity="info" />
          <Chip
            label={`${t(CRITICALITY_LABELS[cvss.severity])} ${t('Criticality').toLowerCase()}`}
            severity={CRITICALITY_SEVERITY[cvss.severity]}
          />
        </div>
      )}
    >
      <div style={{
        marginTop: 'auto',
        display: 'flex',
        flexWrap: 'wrap',
        gap: 8,
      }}
      >
        {occurrences != null && (
          <InfoPill icon={FormatListNumbered} value={occurrences} label={t('Occurrences')} />
        )}
        <InfoPill icon={MonitorCellphone} value={assetsCount} label={t('Impacted assets')} />
        {cvssScore != null && (
          <InfoPill
            icon={ShieldOutline}
            value={cvssScore.toFixed(1)}
            label={t('CVSS score')}
            iconColor="var(--color-feedback-warning-primary)"
          />
        )}
      </div>
    </EntityCard>
  );
};

export default FindingCard;
