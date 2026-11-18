import { Button, CardBanner, Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, useMemo } from 'react';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../../components/common/marketplace/card/MarketplaceCard';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type AttackPattern, type Domain, type ThreatArsenalAction } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../../utils/utils';
import InjectIcon from '../../common/injects/InjectIcon';
import ThreatArsenalActionPopover from '../ThreatArsenalActionPopover';
import actionDomains from '../utils/actionDomains';
import { getStatusLabel, type PayloadStatus, STATUS_CHIP_SEVERITY } from '../utils/threatArsenalStatusUtils';

interface Props {
  action: ThreatArsenalAction;
  onSelect: () => void;
  onUpdate: (result: ThreatArsenalAction) => void;
  onDuplicate: (result: ThreatArsenalAction) => void;
  onDelete: () => void;
  onRunTest: () => void;
}

const ThreatArsenalCard: FunctionComponent<Props> = ({
  action,
  onSelect,
  onUpdate,
  onDuplicate,
  onDelete,
  onRunTest,
}) => {
  const { t, tPick, nsd } = useFormatter();
  const canRunTest = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const allDomains: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const primaryDomain = useMemo(() => actionDomains(action, allDomains)[0], [action, allDomains]);
  const attackPatternsMap: Record<string, AttackPattern> = useHelper((helper: AttackPatternHelper) => helper.getAttackPatternsMap());
  const techniques = (action.action_attack_patterns_ids ?? [])
    .map(id => attackPatternsMap[id]?.attack_pattern_external_id)
    .filter((id): id is string => !!id)
    .sort();
  const name = tPick(action.action_labels);
  const catalog = isFeatureEnabled('CATALOG');
  const revoked = catalog && !!action.action_payload_revoked;
  const status = revoked ? 'REVOKED' : action.action_payload?.payload_status;
  const statusLabel = getStatusLabel(status);
  const platforms = action.action_platforms ?? [];

  return (
    <MarketplaceCard
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
      onClick={onSelect}
      banner={revoked && <CardBanner severity="error">{t('Revoked: injects using it fail')}</CardBanner>}
      chips={techniques.length > 0 && (
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
        ...(catalog
          ? [{
              label: t('Version'),
              value: action.action_payload_version ? `v${action.action_payload_version}` : '–',
            }, {
              label: t('Source'),
              value: action.action_payload ? t(action.action_payload_managed ? 'Filigran' : 'Local') : '–',
            }]
          : []),
        {
          label: t('Updated'),
          value: nsd(action.injector_contract_updated_at),
        },
      ]}
      footerStart={(
        <MarketplaceCardMeta
          status={statusLabel && <Chip label={t(statusLabel)} severity={STATUS_CHIP_SEVERITY[status as PayloadStatus]} />}
          platforms={platforms}
        />
      )}
      action={(
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
        }}
        >
          {canRunTest && !revoked && (
            <Button size="sm" priority="secondary" onClick={onRunTest}>{t('Run a test')}</Button>
          )}
          <ThreatArsenalActionPopover
            action={action}
            onUpdate={onUpdate}
            onDuplicate={onDuplicate}
            onDelete={onDelete}
          />
        </Box>
      )}
    />
  );
};

export default ThreatArsenalCard;
