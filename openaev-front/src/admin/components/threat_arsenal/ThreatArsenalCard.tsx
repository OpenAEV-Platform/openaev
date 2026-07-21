import { CheckCircleOutlined, RadioButtonUncheckedOutlined } from '@mui/icons-material';
import { Box, Card, CardActionArea, Checkbox, Chip, Tooltip, Typography } from '@mui/material';
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
  const accent = primaryDomain?.domain_color ?? theme.palette.primary.main;
  const status = action.action_payload?.payload_status;
  const statusColor = getStatusColor(theme, status);
  const statusLabel = getStatusLabel(status);
  const name = tPick(action.action_labels);

  const showCheckbox = anySelected || selected || checked;

  return (
    <Card
      variant="outlined"
      data-testid="threat-arsenal-card"
      sx={{
        'position': 'relative',
        'display': 'flex',
        'flexDirection': 'column',
        'height': '100%',
        'borderRadius': 1,
        'overflow': 'hidden',
        'borderColor': selected ? accent : theme.palette.divider,
        'backgroundColor': selected
          ? alpha(accent, 0.06)
          : theme.palette.background.paper,
        'transition': theme.transitions.create(
          ['border-color', 'box-shadow', 'transform'],
          { duration: theme.transitions.duration.shorter },
        ),
        // Signature marketplace hover (same as the Integrations cards), tinted
        // with the domain accent instead of the primary color.
        '&:hover': {
          borderColor: alpha(accent, 0.3),
          boxShadow: `0 0 30px ${alpha(accent, 0.12)}`,
          transform: 'translateY(-2px)',
        },
        '&:hover .threat-card-checkbox': { opacity: 1 },
      }}
    >
      <Box
        aria-hidden
        sx={{
          height: 64,
          background: `linear-gradient(135deg, ${alpha(accent, 0.22)} 0%, ${alpha(accent, 0.04)} 100%)`,
          borderBottom: `1px solid ${alpha(accent, 0.18)}`,
          position: 'relative',
        }}
      >
        <Box
          sx={{
            position: 'absolute',
            left: 16,
            bottom: -22,
            width: 44,
            height: 44,
            borderRadius: 1.5,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: theme.palette.background.paper,
            border: `1px solid ${alpha(accent, 0.4)}`,
            boxShadow: `0 4px 12px -4px ${alpha(accent, 0.4)}`,
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

        {statusLabel && (
          <Tooltip title={t(statusLabel)} enterDelay={400}>
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
                backgroundColor: alpha(statusColor, 0.2),
                color: statusColor,
                border: `1px solid ${alpha(statusColor, 0.45)}`,
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
                  boxShadow: `0 0 6px ${alpha(statusColor, 0.8)}`,
                }}
              />
              {t(statusLabel)}
            </Box>
          </Tooltip>
        )}
      </Box>

      <Box
        className="threat-card-checkbox"
        sx={{
          'position': 'absolute',
          'top': 10,
          'left': 10,
          'zIndex': 2,
          'opacity': showCheckbox ? 1 : 0,
          'transition': theme.transitions.create(['opacity', 'transform']),
          '& .MuiCheckbox-root': {
            'padding': 0.5,
            'color': alpha('#fff', 0.85),
            '&.Mui-checked': { color: theme.palette.primary.main },
            '&:hover': { backgroundColor: alpha('#fff', 0.08) },
          },
          '& .MuiSvgIcon-root': {
            fontSize: 22,
            filter: `drop-shadow(0 1px 2px ${alpha('#000', 0.5)})`,
          },
        }}
        onClick={(event) => {
          event.stopPropagation();
          onToggleEntity(event);
        }}
      >
        <Checkbox
          checked={checked}
          disableRipple
          size="small"
          icon={<RadioButtonUncheckedOutlined />}
          checkedIcon={<CheckCircleOutlined />}
          slotProps={{ input: { 'aria-label': name } }}
        />
      </Box>

      <Box
        sx={{
          'position': 'absolute',
          'top': 6,
          'right': 6,
          'zIndex': 2,
          '& .MuiIconButton-root': {
            'color': alpha('#fff', 0.85),
            'padding': 0.75,
            'transition': theme.transitions.create('background-color'),
            '&:hover': { backgroundColor: alpha('#fff', 0.08) },
          },
          '& .MuiSvgIcon-root': {
            fontSize: 20,
            filter: `drop-shadow(0 1px 2px ${alpha('#000', 0.5)})`,
          },
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

      <CardActionArea
        onClick={onSelect}
        sx={{
          'flex': 1,
          'paddingTop': 4,
          'paddingInline': 2,
          'paddingBottom': 1.5,
          'display': 'flex',
          'flexDirection': 'column',
          'gap': 1,
          'alignItems': 'stretch',
          'justifyContent': 'flex-start',
          '& .MuiCardActionArea-focusHighlight': { background: 'transparent' },
        }}
      >
        <Tooltip title={name} enterDelay={500}>
          <Typography
            sx={{
              fontSize: 13.5,
              fontWeight: 600,
              lineHeight: 1.35,
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              wordBreak: 'break-word',
              minHeight: 36,
            }}
          >
            {name}
          </Typography>
        </Tooltip>

        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          flexWrap: 'wrap',
          minHeight: 22,
        }}
        >
          {primaryDomain && (
            <Chip
              size="small"
              label={primaryDomain.domain_name}
              variant="outlined"
              sx={{
                height: 20,
                fontSize: 10.5,
                fontWeight: 600,
                letterSpacing: '0.02em',
                textTransform: 'uppercase',
                borderColor: alpha(accent, 0.5),
                color: accent,
                backgroundColor: alpha(accent, 0.08),
                borderRadius: 0.75,
              }}
            />
          )}
          {domains.length > 1 && (
            <Tooltip title={domains.slice(1).map(d => d.domain_name).join(', ')}>
              <Chip
                size="small"
                label={`+${domains.length - 1}`}
                variant="outlined"
                sx={{
                  height: 20,
                  fontSize: 10.5,
                  borderRadius: 0.75,
                }}
              />
            </Tooltip>
          )}
        </Box>

        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            marginTop: 'auto',
            paddingTop: 1,
            minHeight: 28,
            borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
          }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
            flexShrink: 0,
          }}
          >
            {(action.action_platforms ?? []).slice(0, 4).map(platform => (
              <PlatformIcon
                key={platform}
                width={16}
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
            flex: 1,
            display: 'flex',
            justifyContent: 'flex-end',
            overflow: 'hidden',
          }}
          >
            <ItemTags variant="reduced-view" tags={action.action_tags_ids} />
          </Box>
        </Box>

        <Typography
          variant="caption"
          sx={{
            color: 'text.disabled',
            fontSize: 11,
            marginTop: 0.25,
          }}
        >
          {t('Updated')}
          {' '}
          {nsdt(action.injector_contract_updated_at)}
        </Typography>
      </CardActionArea>
    </Card>
  );
};

export default ThreatArsenalCard;
