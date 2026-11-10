import { Button, Chip, type ChipSeverity, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { GroupsOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useMemo } from 'react';

import type { AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import type { DomainHelper } from '../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../components/common/marketplace/MarketplaceCard';
import MarketplaceCardBanner from '../../../components/common/marketplace/MarketplaceCardBanner';
import usePlatformVersion from '../../../components/common/marketplace/usePlatformVersion';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import { type AttackPattern, type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import InjectIcon from '../common/injects/InjectIcon';
import { isBuiltinConnector } from '../integrations/common/builtinConnectorDescriptions';
import { approvalStatusLabel, approvalStatusSeverity, NEW_ACTION_HINT, NEW_VERSION_HINT } from './approval/approvalStatusUtils';
import { useApprovedEarlierVersion } from './approval/approvedEarlierVersion';
import { PAYLOAD_ORIGIN_LABELS, payloadOrigin } from './payloadOrigin';
import ThreatArsenalActionPopover from './ThreatArsenalActionPopover';
import { actionDomains } from './threatArsenalDomainSections';
import { getStatusHint, getStatusLabel, type PayloadStatus, STATUS_CHIP_SEVERITY } from './threatArsenalStatusUtils';

interface Props {
  action: ThreatArsenalAction;
  selected: boolean;
  onSelect: () => void;
  onUpdate: (result: ThreatArsenalAction) => void;
  onDuplicate: (result: ThreatArsenalAction, open?: boolean) => void;
  onDelete: () => void;
  onRunTest: () => void;
  disableUpdate: boolean;
  disableDuplicate: boolean;
  disableJsonExport: boolean;
  disableDelete: boolean;
  /** Off on the review pages: a reviewer decides on a version, they do not run it from there. */
  showRunTest?: boolean;
  /** The card's action in place of Run a test, e.g. Review on the review pages. */
  primaryAction?: {
    label: string;
    onClick: () => void;
  };
  /** On the review pages the card shows what a decision needs: who submitted which version, and when; no migration banner. */
  review?: { submittedBy?: string };
}

// A tag stays short: past its width it truncates, and the hover always says it in full.
const HintChip: FunctionComponent<{
  label: string;
  hint: string;
  severity?: ChipSeverity;
  maxWidth: number;
}> = ({ label, hint, severity, maxWidth }) => (
  <Tooltip>
    <TooltipTrigger asChild>
      <span
        className="inline-flex"
        style={{
          maxWidth,
          minWidth: 0,
        }}
      >
        <Chip label={label} severity={severity} style={{ maxWidth: '100%' }} />
      </span>
    </TooltipTrigger>
    <TooltipContent>{hint}</TooltipContent>
  </Tooltip>
);

const ThreatArsenalCard: FunctionComponent<Props> = ({
  action,
  selected,
  onSelect,
  onUpdate,
  onDuplicate,
  onDelete,
  onRunTest,
  disableUpdate,
  disableDuplicate,
  disableJsonExport,
  disableDelete,
  showRunTest = true,
  primaryAction,
  review,
}) => {
  const { t, tPick, nsd } = useFormatter();
  const theme = useTheme();
  const version = action.action_payload?.payload_version;
  const platformVersion = usePlatformVersion();
  // Contracts of injectors shipped with the platform: no integration to deploy.
  const builtIn = !action.action_payload && isBuiltinConnector(action.action_injector_type);

  const allDomains: Domain[] = useHelper(
    (helper: DomainHelper) => helper.getDomains(),
  );
  const attackPatternsMap: Record<string, AttackPattern> = useHelper((helper: AttackPatternHelper) => helper.getAttackPatternsMap());
  const techniques = (action.action_attack_patterns_ids ?? [])
    .map(id => attackPatternsMap[id]?.attack_pattern_external_id)
    .filter((id): id is string => !!id)
    .sort();

  const domains = useMemo(() => actionDomains(action, allDomains), [action, allDomains]);

  const primaryDomain = domains[0];
  const accent = primaryDomain?.domain_color ?? theme.palette.primary.main;
  const status = action.action_payload?.payload_revoked ? 'REVOKED' : action.action_payload?.payload_status;
  const statusLabel = getStatusLabel(status);
  const statusHint = getStatusHint(status);
  // Approval and Verified / Unverified / Deprecated are two different things (is it trusted by a
  // second person, does it work): each keeps its own slot so neither masks the other. The approval
  // chip only shows when it needs attention, on top with the other "to do" chips; the lifecycle
  // status stays in the footer. Same card on every Threat Arsenal tab.
  // The status of the version listed (each version is decided on its own).
  const approvalStatus = action.action_payload?.payload_approval_status;
  const approvalNeedsAttention = approvalStatus === 'PENDING' || approvalStatus === 'REJECTED' || approvalStatus === 'SUPERSEDED';
  // New version only when an earlier version was approved (it keeps running); otherwise a new action,
  // even after several saves (a superseded save was never approved).
  const isNewVersion = useApprovedEarlierVersion(
    action.injector_contract_id,
    approvalNeedsAttention,
    (action.action_payload?.payload_installed_versions ?? 0) > 1,
  );
  // Approval as two short tags: what waits (new action / new version) and its state (pending /
  // rejected); the hover says it in full.
  const approvalKind = isNewVersion ? t('New version') : t('New action');
  const approvalState = approvalStatus && approvalStatus !== 'PENDING' ? t(approvalStatusLabel(approvalStatus)) : t('Pending');
  // The kind chip says what it is, the state chip where it stands.
  const kindHint = t(isNewVersion ? NEW_VERSION_HINT : NEW_ACTION_HINT);
  const stateHint = (() => {
    if (approvalStatus === 'REJECTED') return t('Rejected: edit it to submit it for approval again.');
    if (approvalStatus === 'SUPERSEDED') return t('Superseded: a newer version replaced it before any decision.');
    return t('Pending: waits for someone who can approve content to decide.');
  })();
  const name = tPick(action.action_labels);
  // Trust line (idea A): version and source together, as in the drawer.
  const origin = payloadOrigin(action.action_payload);
  const olderInUse = action.action_payload?.payload_older_versions_in_use ?? 0;
  // Running a test creates an atomic test: hidden without that right, as the PRD hides what a user cannot do.
  const canRunTest = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  // The one thing to act on, in the Figma CardBanner: revoked first, then injects left behind.
  const versionsLink = `/admin/threat-arsenal?action=${encodeURIComponent(action.injector_contract_id)}&tab=versions`;
  const migrateLink = `${versionsLink}&focus=migrate`;
  let banner: ReactNode = null;
  if (review) {
    banner = null;
  } else if (action.action_payload?.payload_revoked) {
    banner = (
      <MarketplaceCardBanner
        tone="critical"
        message={t('Injects using it are skipped')}
        link={{
          label: t('Versions'),
          to: versionsLink,
        }}
      />
    );
  } else if (olderInUse > 0) {
    banner = (
      <MarketplaceCardBanner
        tone="warning"
        message={olderInUse === 1 ? t('1 inject runs an older version') : t('{count} injects run an older version', { count: String(olderInUse) })}
        link={{
          label: t('Migrate'),
          to: migrateLink,
        }}
      />
    );
  }

  return (
    <MarketplaceCard
      accent={accent}
      testId="threat-arsenal-card"
      icon={(
        <InjectIcon
          type={
            action.action_payload != null
              ? action.action_payload.payload_collector_type ?? action.action_payload.payload_type
              : action.action_injector_type
          }
          isPayload={action.action_payload != null}
          variant="list"
        />
      )}
      title={name}
      overline={primaryDomain ? t(primaryDomain.domain_name) : undefined}
      subtitle={action.action_author_name ? `${t('by')} ${action.action_author_name}` : undefined}
      // Not on review cards: what a reviewer needs is what changed, which the Versions tab shows.
      description={review ? undefined : action.action_payload?.payload_description ?? undefined}
      selected={selected}
      onClick={onSelect}
      banner={banner}
      // The grid sets the height (DS Card rule): cards of a row align, no fixed gap.
      height="100%"
      tags={techniques.length > 0 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="inline-flex">
              <Chip label={techniques.length > 1 ? `${techniques[0]} +${techniques.length - 1}` : techniques[0]} />
            </span>
          </TooltipTrigger>
          <TooltipContent>{techniques.join(', ')}</TooltipContent>
        </Tooltip>
      )}
      // Always the same three columns, so Version, Source and Updated line up across the grid.
      info={review
        ? [
            {
              label: t('Version'),
              value: version ? `v${version}` : '–',
            },
            {
              label: t('Submitted by'),
              value: review.submittedBy ?? action.action_author_name ?? '–',
            },
            {
              label: t('Submitted'),
              value: nsd(action.injector_contract_updated_at),
            },
          ]
        : [
            {
              label: t('Version'),
              value: (version && `v${version}`) || (builtIn && platformVersion ? `v${platformVersion}` : '–'),
            },
            {
              label: t('Source'),
              value: builtIn ? t('Built-in') : (origin && t(PAYLOAD_ORIGIN_LABELS[origin])) || '–',
            },
            {
              label: t('Updated'),
              value: nsd(action.injector_contract_updated_at),
            },
          ]}
      chips={null}
      // Approval just above the strip, bottom left, only when it needs attention: the strip keeps
      // the DS layout (one status and the platforms), so nothing truncates.
      statusRow={approvalNeedsAttention && (
        <>
          <HintChip label={approvalKind} hint={kindHint} maxWidth={140} />
          <HintChip label={approvalState} hint={stateHint} severity={approvalStatus ? approvalStatusSeverity(approvalStatus) : undefined} maxWidth={120} />
        </>
      )}
      platforms={action.action_targets_teams ? [] : action.action_platforms ?? []}
      // Figma Card footer (DS "Threat arsenal" in situ): statuses and platforms bottom left, the action
      // and the menu bottom right, on the full-bleed strip.
      metaStart={(
        <>
          {statusLabel && (
            <HintChip
              label={t(statusLabel)}
              hint={t(statusHint ?? statusLabel)}
              severity={STATUS_CHIP_SEVERITY[status as PayloadStatus]}
              maxWidth={96}
            />
          )}
          {action.action_targets_teams
            ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <GroupsOutlined sx={{
                      fontSize: 18,
                      color: 'text.secondary',
                    }}
                    />
                  </TooltipTrigger>
                  <TooltipContent>{t('Targets teams')}</TooltipContent>
                </Tooltip>
              )
            : (action.action_platforms ?? []).slice(0, 3).map(platform => <PlatformIcon key={platform} width={16} platform={platform} tooltip />)}
        </>
      )}
      action={(
        <span
          onClick={event => event.stopPropagation()}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
          }}
        >
          {primaryAction && <Button size="sm" priority="secondary" onClick={primaryAction.onClick}>{primaryAction.label}</Button>}
          {!primaryAction && showRunTest && canRunTest && !action.action_payload?.payload_revoked && (
            <Button size="sm" priority="secondary" onClick={onRunTest}>{t('Run a test')}</Button>
          )}
          <ThreatArsenalActionPopover
            actionId={action.injector_contract_id}
            payloadId={action.action_payload?.payload_id ?? ''}
            name={name}
            onUpdate={onUpdate}
            onDuplicate={onDuplicate}
            onDelete={onDelete}
            disableUpdate={disableUpdate}
            disableDuplicate={disableDuplicate}
            disableJsonExport={disableJsonExport}
            disableDelete={disableDelete}
            revoked={action.action_payload?.payload_revoked}
            managed={!!action.action_payload?.payload_managed}
            variantIdentityId={action.action_payload?.payload_variant ? action.action_payload.payload_identity_id : undefined}
            onRevocationChange={(revoked, status) => action.action_payload && onUpdate({
              ...action,
              action_payload: {
                ...action.action_payload,
                payload_revoked: revoked,
                payload_status: status ?? action.action_payload.payload_status,
              },
            })}
            onApprovalChange={status => action.action_payload && onUpdate({
              ...action,
              action_payload: {
                ...action.action_payload,
                payload_approval_status: status ?? action.action_payload.payload_approval_status,
              },
            })}
          />
        </span>
      )}
    />
  );
};

export default ThreatArsenalCard;
