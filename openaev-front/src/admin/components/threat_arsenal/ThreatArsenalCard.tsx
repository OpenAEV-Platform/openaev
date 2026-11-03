import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent, type MouseEvent, useMemo } from 'react';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import EntityCard from '../../../components/common/card/EntityCard';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import { type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { TO_CLASSIFY } from '../../../utils/domains/domainUtils';
import { buildTenantApiPath } from '../../../utils/url-helper';
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
const MAX_DOMAINS = 2;
// Height of the Figma card (Design_System_2026, node 8470:25385).
const CARD_HEIGHT = 210;

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

  const visibleDomains = domains.slice(0, MAX_DOMAINS);
  const hiddenDomainsLabel = domains.slice(MAX_DOMAINS).map(d => t(d.domain_name)).join(', ');
  const payload = action.action_payload;
  const status = payload?.payload_status;
  const statusLabel = getStatusLabel(status);
  const name = tPick(action.action_labels);
  const platforms = action.action_platforms ?? [];
  const iconType = payload != null ? payload.payload_collector_type ?? payload.payload_type : action.action_injector_type;
  // Same logos as InjectIcon: injectors and collector payloads have an image,
  // which fills the thumbnail; the other payload types are 24px glyphs.
  let thumbnailImage: {
    src: string;
    alt: string;
  } | undefined;
  if (iconType && payload == null) {
    thumbnailImage = {
      src: buildTenantApiPath(`/api/injectors/${iconType}/image`),
      alt: t(iconType),
    };
  } else if (iconType?.startsWith('openaev_')) {
    thumbnailImage = {
      src: buildTenantApiPath(`/api/collectors/${iconType}/image`),
      alt: t(iconType),
    };
  }

  return (
    // DS 2026 layout (Figma Design_System_2026, node 8470:25385): icon and
    // title, domain chips, last update line, then a footer band with the
    // payload status and the platforms on the left and the ⋮ menu on the right.
    // The checkbox sits on the top right corner of the header.
    <EntityCard
      testId="threat-arsenal-card"
      layout="ds2026"
      ds2026Height={CARD_HEIGHT}
      onClick={onSelect}
      selected={selected}
      image={thumbnailImage}
      icon={<InjectIcon type={iconType} isPayload={payload != null} />}
      title={name}
      tags={domains.length > 0 ? (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          minWidth: 0,
        }}
        >
          {visibleDomains.map(domain => (
            <Chip key={domain.domain_id} label={t(domain.domain_name)} color={domain.domain_color} style={{ maxWidth: '100%' }} />
          ))}
          {domains.length > MAX_DOMAINS && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip label={`+${domains.length - MAX_DOMAINS}`} />
              </TooltipTrigger>
              {hiddenDomainsLabel && <TooltipContent>{hiddenDomainsLabel}</TooltipContent>}
            </Tooltip>
          )}
        </div>
      ) : undefined}
      meta={{
        label: `${t('Updated')}:`,
        value: nsdt(action.injector_contract_updated_at),
      }}
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
          gap: 8,
          minWidth: 0,
        }}
        >
          {statusLabel && <Chip label={t(statusLabel)} severity={getStatusSeverity(status)} />}
          {platforms.slice(0, MAX_PLATFORMS).map(platform => (
            <PlatformIcon
              key={platform}
              width={24}
              platform={platform}
              tooltip
            />
          ))}
          {platforms.length > MAX_PLATFORMS && (
            <span className="text-default-secondary">{`+${platforms.length - MAX_PLATFORMS}`}</span>
          )}
        </div>
      )}
    />
  );
};

export default ThreatArsenalCard;
