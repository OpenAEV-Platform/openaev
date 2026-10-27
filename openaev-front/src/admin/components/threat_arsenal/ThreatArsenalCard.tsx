import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { GroupsOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import type { AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import type { DomainHelper } from '../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../components/common/marketplace/MarketplaceCard';
import usePlatformVersion from '../../../components/common/marketplace/usePlatformVersion';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import { type AttackPattern, type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import InjectIcon from '../common/injects/InjectIcon';
import { isBuiltinConnector } from '../integrations/common/builtinConnectorDescriptions';
import ThreatArsenalActionPopover from './ThreatArsenalActionPopover';
import { actionDomains } from './threatArsenalDomainSections';
import { getStatusLabel, type PayloadStatus, STATUS_CHIP_SEVERITY } from './threatArsenalStatusUtils';
import PayloadVariantBadges from './variants/PayloadVariantBadges';

interface Props {
  action: ThreatArsenalAction;
  selected: boolean;
  onSelect: () => void;
  onUpdate: (result: ThreatArsenalAction) => void;
  onDuplicate: (result: ThreatArsenalAction) => void;
  onDelete: () => void;
  disableUpdate: boolean;
  disableDuplicate: boolean;
  disableJsonExport: boolean;
  disableDelete: boolean;
}

const ThreatArsenalCard: FunctionComponent<Props> = ({
  action,
  selected,
  onSelect,
  onUpdate,
  onDuplicate,
  onDelete,
  disableUpdate,
  disableDuplicate,
  disableJsonExport,
  disableDelete,
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
  const name = tPick(action.action_labels);

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
      chips={(
        <>
          <PayloadVariantBadges payload={action.action_payload} />
          {techniques.length > 0 && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span className="inline-flex">
                  <Chip label={techniques.length > 1 ? `${techniques[0]} +${techniques.length - 1}` : techniques[0]} />
                </span>
              </TooltipTrigger>
              <TooltipContent>{techniques.join(', ')}</TooltipContent>
            </Tooltip>
          )}
        </>
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
      footerRight={(
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          {statusLabel && <Chip label={t(statusLabel)} severity={STATUS_CHIP_SEVERITY[status as PayloadStatus]} />}
        </div>
      )}
      caption={[
        version ? `v${version}` : null,
        builtIn ? `${t('Built-in')}${platformVersion ? ` · v${platformVersion}` : ''}` : null,
        `${t('Updated')} ${nsd(action.injector_contract_updated_at)}`,
      ].filter(Boolean).join(' · ')}
    />
  );
};

export default ThreatArsenalCard;
