import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent, type MouseEvent, useMemo } from 'react';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import EntityCard from '../../../components/common/card/EntityCard';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import { type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { TO_CLASSIFY } from '../../../utils/domains/domainUtils';
import InjectIcon from '../common/injects/InjectIcon';
import ThreatArsenalActionPopover from './ThreatArsenalActionPopover';
import { getStatusLabel, getStatusSeverity } from './threatArsenalStatusUtils';

interface Props {
  action: ThreatArsenalAction;
  selected: boolean;
  checked: boolean;
  anySelected: boolean;
  onSelect: () => void;
  onToggleEntity: (event: MouseEvent<HTMLElement>) => void;
  onUpdate: (result: ThreatArsenalAction) => void;
  onDuplicate: (result: ThreatArsenalAction) => void;
  onDelete: () => void;
  disableUpdate: boolean;
  disableDuplicate: boolean;
  disableJsonExport: boolean;
  disableDelete: boolean;
}

const MAX_PLATFORMS = 4;

const ThreatArsenalCard: FunctionComponent<Props> = ({
  action,
  selected,
  checked,
  anySelected,
  onSelect,
  onToggleEntity,
  onUpdate,
  onDuplicate,
  onDelete,
  disableUpdate,
  disableDuplicate,
  disableJsonExport,
  disableDelete,
}) => {
  const { t, tPick, nsdt } = useFormatter();

  const allDomains: Domain[] = useHelper(
    (helper: DomainHelper) => helper.getDomains(),
  );

  const domains = useMemo(() => {
    if (!action.action_domains_ids) return [] as Domain[];
    return allDomains.filter(
      d => action.action_domains_ids?.includes(d.domain_id) && d.domain_name !== TO_CLASSIFY,
    );
  }, [action.action_domains_ids, allDomains]);

  const primaryDomain = domains[0];
  const secondaryDomainsLabel = domains.slice(1).map(d => t(d.domain_name)).join(', ');
  const payload = action.action_payload;
  const status = payload?.payload_status;
  const statusLabel = getStatusLabel(status);
  const name = tPick(action.action_labels);
  const platforms = action.action_platforms ?? [];

  return (
    <EntityCard
      testId="threat-arsenal-card"
      onClick={onSelect}
      selected={selected}
      icon={(
        <InjectIcon
          type={payload != null ? payload.payload_collector_type ?? payload.payload_type : action.action_injector_type}
          isPayload={payload != null}
          variant="list"
        />
      )}
      overline={payload?.payload_type ? t(payload.payload_type) : t('Injector')}
      title={name}
      caption={`${t('Updated')} ${nsdt(action.injector_contract_updated_at)}`}
      selection={{
        checked,
        alwaysVisible: anySelected || selected,
        onToggle: onToggleEntity,
        label: name,
      }}
      menu={(
        <ThreatArsenalActionPopover
          actionId={action.injector_contract_id}
          payloadId={payload?.payload_id ?? ''}
          name={name}
          onUpdate={onUpdate}
          onDuplicate={onDuplicate}
          onDelete={onDelete}
          disableUpdate={disableUpdate}
          disableDuplicate={disableDuplicate}
          disableJsonExport={disableJsonExport}
          disableDelete={disableDelete}
        />
      )}
      footerStart={(
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          minWidth: 0,
        }}
        >
          {statusLabel && <Chip label={t(statusLabel)} severity={getStatusSeverity(status)} />}
          {primaryDomain && <Chip label={t(primaryDomain.domain_name)} color={primaryDomain.domain_color} />}
          {domains.length > 1 && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip label={`+${domains.length - 1}`} />
              </TooltipTrigger>
              {secondaryDomainsLabel && <TooltipContent>{secondaryDomainsLabel}</TooltipContent>}
            </Tooltip>
          )}
        </div>
      )}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          flexShrink: 0,
        }}
        >
          {platforms.slice(0, MAX_PLATFORMS).map(platform => (
            <PlatformIcon
              key={platform}
              width={16}
              platform={platform}
              tooltip
            />
          ))}
          {platforms.length > MAX_PLATFORMS && (
            <span className="text-default-secondary">{`+${platforms.length - MAX_PLATFORMS}`}</span>
          )}
        </div>
        <div style={{
          flex: 1,
          display: 'flex',
          justifyContent: 'flex-end',
          overflow: 'hidden',
        }}
        >
          <ItemTags variant="reduced-view" tags={action.action_tags_ids} />
        </div>
      </div>
    </EntityCard>
  );
};

export default ThreatArsenalCard;
