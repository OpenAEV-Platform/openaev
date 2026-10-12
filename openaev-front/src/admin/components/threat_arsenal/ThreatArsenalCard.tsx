import { Checkbox, Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useMemo } from 'react';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import MarketplaceCard from '../../../components/common/marketplace/MarketplaceCard';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import { useHelper } from '../../../store';
import { type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { TO_CLASSIFY } from '../../../utils/domains/domainUtils';
import { tint } from '../../../utils/tint';
import InjectIcon from '../common/injects/InjectIcon';
import ThreatArsenalActionPopover from './ThreatArsenalActionPopover';
import { getStatusColor, getStatusLabel } from './threatArsenalStatusUtils';

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
  const theme = useTheme();

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
  const accent = primaryDomain?.domain_color ?? theme.palette.primary.main;
  const status = action.action_payload?.payload_status;
  const statusColor = getStatusColor(theme, status);
  const statusLabel = getStatusLabel(status);
  const name = tPick(action.action_labels);

  const showCheckbox = anySelected || selected || checked;

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
      subtitle={action.action_author_name ? `${t('by')} ${action.action_author_name}` : undefined}
      selected={selected}
      onClick={onSelect}
      badge={statusLabel && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Box
              sx={{
                position: 'absolute',
                top: 12,
                right: 48,
                paddingInline: 1,
                paddingBlock: 0.25,
                borderRadius: 999,
                fontSize: 10.5,
                fontWeight: 700,
                letterSpacing: '0.04em',
                textTransform: 'uppercase',
                backgroundColor: tint(statusColor, 20),
                color: statusColor,
                border: `1px solid ${tint(statusColor, 45)}`,
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
                backdropFilter: 'blur(4px)',
              }}
            >
              <Box
                aria-hidden
                sx={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  backgroundColor: statusColor,
                  boxShadow: `0 0 6px ${tint(statusColor, 80)}`,
                }}
              />
              {t(statusLabel)}
            </Box>
          </TooltipTrigger>
          <TooltipContent>{t(statusLabel)}</TooltipContent>
        </Tooltip>
      )}
      topLeft={(
        <span
          className="inline-flex"
          onClick={(event) => {
            event.stopPropagation();
            onToggleEntity(event);
          }}
        >
          <Checkbox checked={checked} aria-label={name} />
        </span>
      )}
      topLeftVisible={showCheckbox}
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
        />
      )}
      chips={(
        <>
          {primaryDomain && <Chip label={t(primaryDomain.domain_name)} color={accent} />}
          {domains.length > 1 && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip label={`+${domains.length - 1}`} />
              </TooltipTrigger>
              {secondaryDomainsLabel && <TooltipContent>{secondaryDomainsLabel}</TooltipContent>}
            </Tooltip>
          )}
        </>
      )}
      platforms={action.action_platforms ?? []}
      footerRight={<ItemTags variant="reduced-view" tags={action.action_tags_ids} />}
      caption={`${t('Updated')} ${nsdt(action.injector_contract_updated_at)}`}
    />
  );
};

export default ThreatArsenalCard;
