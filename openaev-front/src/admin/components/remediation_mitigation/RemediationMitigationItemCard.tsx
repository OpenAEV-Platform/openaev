import { Button, Chip, type ChipSeverity, Paper, Text } from '@filigran/design-system';
import { WarningAmberOutlined } from '@mui/icons-material';
import { CheckIndeterminateIcon, EditIcon, LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent, type ReactNode, useState } from 'react';

import CodeBlock from '../../../components/common/overview/CodeBlock';
import { criticalitySeverity } from '../../../components/criticalityColor';
import { useFormatter } from '../../../components/i18n';
import { humanizeEnum } from '../assets/asset-categories';
import { type CombinedCriticality } from './itemCriticality';
import { type AuthorRule, type RemediationMitigationItem } from './remediationMitigationMock';
import { deleteItem, editItem, markReviewed } from './remediationMitigationStore';
import useRemediationMitigationLabels, { AUTHOR_SEVERITY, COLLECTOR_LABELS, PLATFORM_LABELS } from './useRemediationMitigationLabels';

const authorIcon = (author: AuthorRule) => {
  if (author === 'HUMAN') return <EditIcon fontSize="small" />;
  if (author === 'AI') return <LogoXtmOneIcon fontSize="small" />;
  return <CheckIndeterminateIcon fontSize="small" />;
};

const Section: FunctionComponent<{
  title: string;
  children: ReactNode;
}> = ({ title, children }) => (
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
  }}
  >
    <Text variant="content-caption" className="text-default-secondary">{title}</Text>
    {children}
  </div>
);

// Secondary information: closed by default, the rule or the steps stay the first thing to read.
const Collapsible: FunctionComponent<{
  title: string;
  children: ReactNode;
}> = ({ title, children }) => (
  <details>
    <summary style={{ cursor: 'pointer' }}>
      <Text variant="content-caption" className="text-default-secondary">{title}</Text>
    </summary>
    <div style={{ marginTop: 6 }}>{children}</div>
  </details>
);

const RISK_SEVERITY: Record<string, ChipSeverity> = {
  Low: 'low',
  Medium: 'medium',
  High: 'high',
};

// "Medium: a service can fail to start..." -> level (chip) and the rest of the sentence.
const splitRisk = (changeRisk: string) => {
  const match = /^(low|medium|high)\b[:,]?\s*(.*)$/is.exec(changeRisk);
  return match
    ? {
        level: `${match[1].charAt(0).toUpperCase()}${match[1].slice(1).toLowerCase()}`,
        text: match[2],
      }
    : {
        level: undefined,
        text: changeRisk,
      };
};

const BulletList: FunctionComponent<{
  entries: string[];
  ordered?: boolean;
}> = ({ entries, ordered = false }) => {
  const ListTag = ordered ? 'ol' : 'ul';
  return (
    <ListTag style={{
      margin: 0,
      paddingLeft: 20,
    }}
    >
      {entries.map(entry => (
        <li key={entry}><Text variant="content-compact">{entry}</Text></li>
      ))}
    </ListTag>
  );
};

interface Props {
  item: RemediationMitigationItem;
  // Remediation only: the hosts where it must be applied (the targets the action was not prevented on).
  affectedHosts?: string[];
  // Where the item is stored (the action for a remediation, the result for a mitigation).
  scopeKey?: string;
  // How critical the guidance is: the weakness combined with the assets it applies to.
  criticality?: CombinedCriticality;
  // Set on the Threat Arsenal / attack path screens: review, edit and delete are then available.
  editable?: boolean;
  // Types of the findings the action discovered, shown under the title of the recommendation.
  findingTypes?: string[];
  // Prevention items link to the root-cause fix of the same payload.
  onSwitchToRemediation?: () => void;
}

// One remediation or mitigation. The remediation shows its ordered actions, the mitigation its rule.
const RemediationMitigationItemCard = ({
  item,
  affectedHosts = [],
  scopeKey,
  criticality,
  findingTypes = [],
  editable = false,
  onSwitchToRemediation,
}: Props) => {
  const { t } = useFormatter();
  const labels = useRemediationMitigationLabels();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const editableText = item.category === 'mitigation' ? item.content ?? '' : item.summary;
  const needsReview = item.review_state === 'needs_review';
  // A remediation (patch) is made once for the action and never changes: it can be reviewed, not edited or deleted.
  const locked = item.control_type === 'patch';
  // What must be true before applying: the prerequisites of every action and the assumptions, once each.
  const prerequisites = [...new Set([
    ...(item.actions ?? []).flatMap(action => action.prerequisites),
    ...(item.assumptions ?? []),
  ])];

  return (
    <Paper
      padding={16}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      <Text variant="content-base-bold">{item.title}</Text>

      {findingTypes.length > 0 && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 6,
        }}
        >
          <Text variant="content-caption" className="text-default-secondary">{`${t('Discovered findings')}:`}</Text>
          {findingTypes.map(type => <Chip key={type} label={type} severity="info" />)}
        </div>
      )}

      {/* Three chips at most: what it is, where it runs, who wrote it. The vendor is the open tab. */}
      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 6,
      }}
      >
        {criticality && (
          <Chip
            label={`${t('Criticality')}: ${t(humanizeEnum(criticality.level))}`}
            severity={criticalitySeverity(criticality.level)}
          />
        )}
        <Chip label={labels.combinedTag(item)} severity="info" />
        <Chip label={PLATFORM_LABELS[item.platform]} severity="neutral" />
        <Chip startIcon={authorIcon(item.author_rule)} label={labels.author[item.author_rule]} severity={AUTHOR_SEVERITY[item.author_rule]} />
        {item.review_state === 'reviewed' && <Chip label={t('Reviewed')} severity="low" />}
      </div>

      {editing && item.category === 'remediation'
        ? null
        : <Text variant="content-compact">{item.summary}</Text>}

      {/* One warning strip: verify the guidance, and a prevention only blocks the behavior. */}
      {(item.verify || (item.category === 'mitigation' && item.control_type === 'prevention')) && (
        <Paper
          padding={8}
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 8,
          }}
        >
          <WarningAmberOutlined fontSize="small" />
          {item.verify && <Text variant="content-compact">{t('Verify this guidance against your environment before applying it.')}</Text>}
          {item.category === 'mitigation' && item.control_type === 'prevention' && (
            <Text variant="content-compact">
              {t('Blocks the attack behavior. The underlying weakness remains. See Remediation for the root-cause fix.')}
            </Text>
          )}
          {item.category === 'mitigation' && item.control_type === 'prevention' && onSwitchToRemediation && (
            <Button priority="tertiary" size="sm" onClick={onSwitchToRemediation}>{t('See Remediation')}</Button>
          )}
        </Paper>
      )}

      {/* The steps (remediation) are always open; the change risk is a chip next to each action. */}
      {item.actions?.map((action) => {
        const risk = splitRisk(action.change_risk);
        return (
          <Section key={action.title} title={action.title}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 8,
            }}
            >
              <Chip label={`${t('Change risk')}${risk.level ? `: ${t(risk.level)}` : ''}`} severity={risk.level ? RISK_SEVERITY[risk.level] : 'neutral'} />
              {risk.text && <Text variant="content-compact">{risk.text}</Text>}
            </div>
            <BulletList entries={action.steps} ordered />
          </Section>
        );
      })}

      {editing && (
        <textarea
          aria-label={t('Edit')}
          value={draft}
          onChange={event => setDraft(event.target.value)}
          rows={item.category === 'mitigation' ? 10 : 5}
          style={{
            width: '100%',
            fontFamily: 'inherit',
            fontSize: 13,
            padding: 8,
            borderRadius: 4,
            boxSizing: 'border-box',
          }}
        />
      )}

      {/* The rule (mitigation) is always open and never shortened. */}
      {item.content && !editing && (
        <CodeBlock
          content={item.content}
          language={item.collector_type ? COLLECTOR_LABELS[item.collector_type] : PLATFORM_LABELS[item.platform]}
        />
      )}

      {/* Everything else is closed by default. The hosts the remediation must be applied on come first. */}
      {item.category === 'remediation' && affectedHosts.length > 0 && (
        <Collapsible title={`${t('Affected hosts')} (${affectedHosts.length})`}>
          <BulletList entries={affectedHosts} />
        </Collapsible>
      )}
      {/* Prerequisites and assumptions are one list. */}
      {prerequisites.length > 0 && (
        <Collapsible title={t('Prerequisites')}>
          <BulletList entries={prerequisites} />
        </Collapsible>
      )}
      {item.references && item.references.length > 0 && (
        <Collapsible title={t('References')}>
          <BulletList entries={item.references} />
        </Collapsible>
      )}

      {editable && scopeKey && (
        <div style={{
          display: 'flex',
          gap: 8,
          flexWrap: 'wrap',
        }}
        >
          {editing
            ? (
                <>
                  <Button
                    size="sm"
                    onClick={() => {
                      editItem(scopeKey, item.id, draft);
                      setEditing(false);
                    }}
                  >
                    {t('Save')}
                  </Button>
                  <Button size="sm" priority="secondary" onClick={() => setEditing(false)}>{t('Cancel')}</Button>
                </>
              )
            : (
                <>
                  {needsReview && (
                    <Button size="sm" onClick={() => markReviewed(scopeKey, item.id)}>{t('Mark as reviewed')}</Button>
                  )}
                  {!locked && (
                    <>
                      <Button
                        size="sm"
                        priority="secondary"
                        onClick={() => {
                          setDraft(editableText);
                          setEditing(true);
                        }}
                      >
                        {t('Edit')}
                      </Button>
                      <Button size="sm" variant="destructive" priority="secondary" onClick={() => deleteItem(scopeKey, item.id)}>{t('Delete')}</Button>
                    </>
                  )}
                </>
              )}
        </div>
      )}
      {/* Last of the card: the way the criticality is computed. */}
      {criticality && (
        <Collapsible title={t('How the criticality is computed')}>
          <Text variant="content-compact">
            {criticality.topAsset
              ? t('Weakness: {weakness}. Most critical asset it applies to: {asset} ({assetCriticality}). Combined: {combined}.', {
                  weakness: t(humanizeEnum(criticality.base)),
                  asset: criticality.topAsset.host,
                  assetCriticality: t(humanizeEnum(criticality.topAsset.criticality)),
                  combined: t(humanizeEnum(criticality.level)),
                })
              : t('Weakness: {weakness}. No asset criticality is set for the hosts it applies to, so the weakness alone gives the criticality.', { weakness: t(humanizeEnum(criticality.base)) })}
          </Text>
        </Collapsible>
      )}
    </Paper>
  );
};

export default RemediationMitigationItemCard;
