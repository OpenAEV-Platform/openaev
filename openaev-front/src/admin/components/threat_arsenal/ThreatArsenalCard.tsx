import { Button, Chip, type ChipSeverity, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { GroupsOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useMemo } from 'react';

import type { AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import type { DomainHelper } from '../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../components/common/marketplace/MarketplaceCard';
import usePlatformVersion from '../../../components/common/marketplace/usePlatformVersion';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type AttackPattern, type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import InjectIcon from '../common/injects/InjectIcon';
import { isBuiltinConnector } from '../integrations/common/builtinConnectorDescriptions';
import { approvalStatusSeverity, displayedApprovalStatus } from './approval/approvalStatusUtils';
import { PAYLOAD_ORIGIN_LABELS, type PayloadOrigin, payloadOrigin } from './payloadOrigin';
import ThreatArsenalActionPopover from './ThreatArsenalActionPopover';
import ThreatArsenalCardBanner from './ThreatArsenalCardBanner';
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
  const approvalStatus = displayedApprovalStatus(action.action_payload?.payload_approval_status, action.action_payload?.payload_pending_version);
  const approvalNeedsAttention = approvalStatus === 'PENDING' || approvalStatus === 'REJECTED';
  // New action or new version of an installed one (a Scenario Factory version is its own payload).
  const isNewVersion = !!action.action_payload?.payload_pending_version || (action.action_payload?.payload_installed_versions ?? 0) > 1;
  // Approval as two short tags: what waits (new action / new version) and its state (pending /
  // rejected); the hover says it in full.
  const approvalKind = isNewVersion ? t('New version') : t('New action');
  const approvalState = approvalStatus === 'REJECTED' ? t('Rejected') : t('Pending');
  let approvalHint = t('Rejected: edit it to submit it for approval again.');
  if (approvalStatus === 'PENDING') {
    approvalHint = isNewVersion
      ? t('A new version waits for approval. The approved version keeps running until then.')
      : t('A new action waits for approval. It cannot be used until it is approved.');
  }
  const name = tPick(action.action_labels);
  // Trust line (idea A): version and source together, as in the drawer.
  const origin = payloadOrigin(action.action_payload);
  const olderInUse = action.action_payload?.payload_older_versions_in_use ?? 0;
  // Running a test creates an atomic test: hidden without that right, as the PRD hides what a user cannot do.
  const canRunTest = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  // The one thing to act on, in the Figma CardBanner: revoked first, then injects left behind.
  const versionsLink = `/admin/threat-arsenal?action=${encodeURIComponent(action.injector_contract_id)}&tab=versions`;
  let banner: ReactNode = null;
  if (action.action_payload?.payload_revoked) {
    banner = (
      <ThreatArsenalCardBanner
        tone="critical"
        message={t('Revoked: injects using it are skipped')}
        link={{
          label: t('Versions'),
          to: versionsLink,
        }}
      />
    );
  } else if (olderInUse > 0) {
    banner = (
      <ThreatArsenalCardBanner
        tone="warning"
        message={olderInUse === 1 ? t('1 inject runs an older version') : t('{count} injects run an older version', { count: String(olderInUse) })}
        link={{
          label: t('Migrate'),
          to: versionsLink,
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
      // Does it work (Verified...): on the domain line, so the title keeps its width; approval sits at the bottom.
      overlineEnd={statusLabel && (
        <HintChip
          label={t(statusLabel)}
          hint={t(statusHint ?? statusLabel)}
          severity={STATUS_CHIP_SEVERITY[status as PayloadStatus]}
          maxWidth={104}
        />
      )}
      subtitle={action.action_author_name ? `${t('by')} ${action.action_author_name}` : undefined}
      description={action.action_payload?.payload_description ?? undefined}
      selected={selected}
      onClick={onSelect}
      topRight={(
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
        />
      )}
      banner={banner}
      height={320}
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
      info={[
        version
          ? {
              label: t('Version'),
              value: `v${version}`,
            }
          : null,
        origin || builtIn
          ? {
              label: t('Source'),
              value: builtIn ? `${t('Built-in')}${platformVersion ? ` · v${platformVersion}` : ''}` : t(PAYLOAD_ORIGIN_LABELS[origin as PayloadOrigin]),
            }
          : null,
        {
          label: t('Updated'),
          value: nsd(action.injector_contract_updated_at),
        },
      ].filter((item): item is {
        label: string;
        value: string;
      } => item !== null)}
      chips={approvalNeedsAttention && (
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="inline-flex" style={{ gap: 4 }}>
              <Chip label={approvalKind} />
              <Chip label={approvalState} severity={approvalStatusSeverity(approvalStatus)} />
            </span>
          </TooltipTrigger>
          <TooltipContent>{approvalHint}</TooltipContent>
        </Tooltip>
      )}
      // A team target says more than the Internal / Service platforms of these contracts.
      platforms={action.action_targets_teams ? [] : action.action_platforms ?? []}
      target={action.action_targets_teams && (
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
      )}
      footerRight={showRunTest && canRunTest && !action.action_payload?.payload_revoked && (
        <span onClick={event => event.stopPropagation()}>
          <Button size="sm" onClick={onRunTest}>{t('Run a test')}</Button>
        </span>
      )}
    />
  );
};

export default ThreatArsenalCard;
