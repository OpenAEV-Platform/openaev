import { Chip, type ChipSeverity, Paper, Text } from '@filigran/design-system';

import CodeBlock from '../../../components/common/overview/CodeBlock';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import { type Platform } from './remediationMitigationMock';
import { SIGMA_COLLECTOR, SIGMA_RULE_SET, type SigmaLevel, sigmaRulesOf } from './sigmaRules';
import { PLATFORM_LABELS } from './useRemediationMitigationLabels';

interface Props {
  // ATT&CK technique ids of the action, e.g. T1003.001.
  techniques: string[];
  // Platform of the action: only the rules for its logs are shown.
  platform: Platform;
  // CVEs the action checks, e.g. CVE-2021-44228.
  cves?: string[];
}

// Sigma's own level of the alert. It is not the criticality of the weakness or of the asset: the chip says "Rule level".
const LEVEL_SEVERITY: Record<SigmaLevel, ChipSeverity> = {
  critical: 'critical',
  high: 'high',
  medium: 'medium',
  low: 'low',
  informational: 'neutral',
};

const LEVEL_LABELS: Record<SigmaLevel, string> = {
  critical: 'Critical',
  high: 'High',
  medium: 'Medium',
  low: 'Low',
  informational: 'Informational',
};

const ExternalLink = ({ href, children }: {
  href: string;
  children: string;
}) => (
  <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
);

// PROTOTYPE: the Sigma rules of an action are the ones of the SigmaHQ rule set that carry its ATT&CK techniques, synced
// by the Sigma collector. Nothing is generated (Community Edition, no XTM One). The Detection Rule License asks to keep
// the author, a link to the rule (set) and the licence: every card shows the three, and the YAML is kept whole.
const SigmaRuleList = ({ techniques, platform, cves }: Props) => {
  const { t } = useFormatter();
  const rules = sigmaRulesOf(techniques, platform, cves);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
    }}
    >
      {/* The collector that brings the rules. It only syncs them: nothing is generated for an action. */}
      <Paper
        padding={16}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
        }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          flexWrap: 'wrap',
        }}
        >
          <Text variant="content-base-bold">{t(SIGMA_COLLECTOR.name)}</Text>
          <Chip label={t(SIGMA_COLLECTOR.status)} severity="low" />
          <Chip label={t('Community Edition')} severity="neutral" />
        </div>
        <Text variant="content-compact">
          {`${t('Source')}: `}
          <ExternalLink href={SIGMA_RULE_SET.url}>{SIGMA_RULE_SET.name}</ExternalLink>
          {` · ${t('Sync')}: ${t(SIGMA_COLLECTOR.schedule)} · ${t('Last sync')}: ${SIGMA_COLLECTOR.lastSync} · ${t('Rules synced')}: ${SIGMA_COLLECTOR.rulesSynced} · ${t('Rules for this action')}: ${rules.length}`}
        </Text>
        <Text variant="content-caption" className="text-default-secondary">
          {`${t('The collector downloads the rule set and keeps the rules as published. Nothing is generated for an action: the rules shown are the ones that match its techniques. They detect the behavior, they do not block it. Rules are under the')} `}
          <ExternalLink href={SIGMA_RULE_SET.licenseUrl}>{SIGMA_RULE_SET.license}</ExternalLink>
          .
        </Text>
      </Paper>
      {rules.length === 0 && <Empty message={t('No Sigma rule matches the techniques of this action.')} />}
      {rules.map(rule => (
        <Paper
          key={rule.id}
          padding={16}
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
          }}
        >
          <Text variant="content-base-bold">{rule.title}</Text>
          <div style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 6,
          }}
          >
            <Chip label={`${t('Rule level')}: ${t(LEVEL_LABELS[rule.level])}`} severity={LEVEL_SEVERITY[rule.level]} />
            <Chip label={rule.status} severity="neutral" />
            <Chip label={PLATFORM_LABELS[rule.platform]} severity="neutral" />
            {rule.techniques.map(technique => <Chip key={technique} label={technique} severity="info" />)}
          </div>
          {/* The three things the licence asks to keep: author, link to the rule, the licence. */}
          <Text variant="content-compact">
            {`${t('Author')}: ${rule.author} · `}
            <ExternalLink href={rule.url ?? SIGMA_RULE_SET.url}>{t('Rule')}</ExternalLink>
            {` · ${t('Licensed under the')} `}
            <ExternalLink href={SIGMA_RULE_SET.licenseUrl}>{SIGMA_RULE_SET.license}</ExternalLink>
          </Text>
          <CodeBlock content={rule.content} language="Sigma" />
        </Paper>
      ))}
    </div>
  );
};

export default SigmaRuleList;
