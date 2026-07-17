import { Box, Checkbox, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useMemo } from 'react';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import PlatformIcon from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import { type Domain, type ThreatArsenalAction } from '../../../utils/api-types';
import { TO_CLASSIFY } from '../../../utils/domains/domainUtils';
import InjectIcon from '../common/injects/InjectIcon';
import PayloadStatusComponent from '../payloads/PayloadStatusComponent';
import ThreatArsenalActionPopover from './ThreatArsenalActionPopover';

interface Props {
  action: ThreatArsenalAction;
  selected: boolean;
  checked: boolean;
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

const ThreatArsenalListRow: FunctionComponent<Props> = ({
  action,
  selected,
  checked,
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
  const { tPick, nsdt } = useFormatter();
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
  const accent = primaryDomain?.domain_color ?? theme.palette.primary.main;
  const name = tPick(action.action_labels);

  return (
    <Box
      role="row"
      tabIndex={0}
      aria-selected={selected}
      onClick={onSelect}
      onKeyDown={(event) => {
        // Mirror the click behaviour for keyboard users so list view stays
        // operable without a mouse. Space/Enter scroll the page by default,
        // so prevent that explicitly.
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          onSelect();
        }
      }}
      sx={{
        'display': 'grid',
        'gridTemplateColumns': '40px 44px minmax(0, 2fr) minmax(0, 1.2fr) 120px 130px 120px 160px 48px',
        'alignItems': 'center',
        'gap': 1.5,
        'paddingBlock': 0.75,
        'paddingInline': 1.5,
        'borderRadius': 1,
        'borderLeft': '3px solid',
        'borderLeftColor': selected ? accent : 'transparent',
        'cursor': 'pointer',
        'backgroundColor': selected ? alpha(accent, 0.08) : 'transparent',
        'transition': theme.transitions.create(['background-color', 'border-color']),
        '&:hover': { backgroundColor: alpha(theme.palette.text.primary, 0.04) },
        '&:focus-visible': {
          outline: `2px solid ${theme.palette.primary.main}`,
          outlineOffset: -2,
        },
      }}
    >
      <Box
        onClick={(event) => {
          event.stopPropagation();
          onToggleEntity(event);
        }}
      >
        <Checkbox
          edge="start"
          checked={checked}
          disableRipple
          size="small"
        />
      </Box>

      <Box
        sx={{
          width: 36,
          height: 36,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: alpha(accent, 0.12),
          border: `1px solid ${alpha(accent, 0.25)}`,
        }}
      >
        <InjectIcon
          type={
            action.action_payload != null
              ? action.action_payload.payload_collector_type ?? action.action_payload.payload_type
              : action.action_injector_type
          }
          isPayload={action.action_payload != null}
          variant="list"
        />
      </Box>

      <Tooltip title={name} enterDelay={500}>
        <Typography
          variant="body2"
          sx={{
            fontWeight: 600,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            minWidth: 0,
          }}
        >
          {name}
        </Typography>
      </Tooltip>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.5,
        overflow: 'hidden',
      }}
      >
        {primaryDomain && (
          <Box
            sx={{
              paddingInline: 1,
              paddingBlock: 0.25,
              borderRadius: 0.75,
              fontSize: 10.5,
              fontWeight: 600,
              letterSpacing: '0.02em',
              textTransform: 'uppercase',
              borderColor: alpha(accent, 0.5),
              border: '1px solid',
              color: accent,
              backgroundColor: alpha(accent, 0.08),
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              maxWidth: '100%',
            }}
          >
            {primaryDomain.domain_name}
          </Box>
        )}
        {domains.length > 1 && (
          <Tooltip title={domains.slice(1).map(d => d.domain_name).join(', ')}>
            <Box
              sx={{
                paddingInline: 0.75,
                paddingBlock: 0.25,
                borderRadius: 0.75,
                fontSize: 10.5,
                border: `1px solid ${theme.palette.divider}`,
                color: 'text.secondary',
              }}
            >
              +
              {domains.length - 1}
            </Box>
          </Tooltip>
        )}
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.75,
      }}
      >
        {(action.action_platforms ?? []).slice(0, 4).map(platform => (
          <PlatformIcon
            key={platform}
            width={18}
            platform={platform}
            tooltip
          />
        ))}
        {(action.action_platforms?.length ?? 0) > 4 && (
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            +
            {(action.action_platforms?.length ?? 0) - 4}
          </Typography>
        )}
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        overflow: 'hidden',
      }}
      >
        <ItemTags variant="reduced-view" tags={action.action_tags_ids} />
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
      }}
      >
        <PayloadStatusComponent status={action.action_payload?.payload_status} />
      </Box>

      <Typography
        variant="caption"
        sx={{
          color: 'text.secondary',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {nsdt(action.injector_contract_updated_at)}
      </Typography>

      <Box
        sx={{
          display: 'flex',
          justifyContent: 'flex-end',
        }}
        onClick={event => event.stopPropagation()}
      >
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
      </Box>
    </Box>
  );
};

export default ThreatArsenalListRow;
