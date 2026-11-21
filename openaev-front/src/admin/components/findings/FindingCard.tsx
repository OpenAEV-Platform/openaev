import {
  Card,
  CardHeader,
  CardInfo,
  CardInfoGroup,
  CardOverview,
  CardTitle,
  Chip,
  HeroStat,
  type HeroStatSeverity,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { fetchFindingSummary } from '../../../actions/findings/finding-actions';
import { fetchVulnerabilityByExternalId } from '../../../actions/vulnerability-actions';
import CardThumbnail from '../../../components/common/card/CardThumbnail';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import { type AggregatedFindingOutput, type FindingSummaryOutput, type VulnerabilityOutput } from '../../../utils/api-types';
import { getSeverityAndColor, type SeverityAndColor } from '../../../utils/Colors';
import ContractOutputElementType from './ContractOutputElementType';

// CVSS v3.1 qualitative rating -> chip tone, as in the Figma footer
// (low green, medium amber, high red).
const CRITICALITY_SEVERITY: Record<SeverityAndColor['severity'], HeroStatSeverity> = {
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

interface Props { finding: AggregatedFindingOutput }

const FindingCard: FunctionComponent<Props> = ({ finding }) => {
  const { t, fldt } = useFormatter();
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
    // Library Card (Figma Design_System_2026, node 8745:64999): finding type
    // and value, the CVSS and criticality chips of a CVE, the figures (library
    // HeroStat) and the last seen line. No footer. The height fits the
    // content; the grid gives every card of the page the tallest one's height.
    <Card data-testid="finding-card" to={`/admin/findings/${finding.finding_id}`} linkComponent={Link}>
      <CardHeader icon={<CardThumbnail icon={<FindingIcon findingType={finding.finding_type} />} />}>
        <CardOverview>{t(ContractOutputElementType[finding.finding_type] ?? finding.finding_type)}</CardOverview>
        <CardTitle>{finding.finding_value}</CardTitle>
      </CardHeader>
      {cvss && (
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
      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 8,
      }}
      >
        {occurrences != null && (
          <HeroStat icon="list-ordered" value={occurrences} label={t('Occurrences')} severity="info" />
        )}
        <HeroStat icon="monitor-smartphone" value={assetsCount} label={t('Impacted assets')} severity="info" />
        {cvss && (
          // The library has no plain `shield` glyph: `shield-check` is its closest one.
          <HeroStat
            icon="shield-check"
            value={cvssScore?.toFixed(1)}
            label={t('CVSS score')}
            severity={CRITICALITY_SEVERITY[cvss.severity]}
          />
        )}
      </div>
      {/* Group-wide last seen (the aggregated list already carries it). */}
      <CardInfoGroup>
        <CardInfo label={`${t('Last seen')}:`} value={fldt(finding.finding_updated_at)} />
      </CardInfoGroup>
    </Card>
  );
};

export default FindingCard;
