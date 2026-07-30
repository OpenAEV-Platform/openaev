import { AccountTreeOutlined, ScheduleOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position, type XYPosition } from '@xyflow/react';
import moment from 'moment';
import { memo, type MouseEvent } from 'react';

import { type InjectOutputType, type InjectStore } from '../../actions/injects/Inject';
import InjectIcon from '../../admin/components/common/injects/InjectIcon';
import InjectPopover from '../../admin/components/common/injects/InjectPopover';
import { isNotEmptyField } from '../../utils/utils';
import { useFormatter } from '../i18n';
import { formatRelativeTime, NODE_WIDTH } from './chronoUtils';

export type InjectNodeData = {
  inject?: InjectOutputType;
  targets: string[];
  startDate?: string;
  /** Auto-layout: the row the card is locked to while dragging horizontally. */
  fixedY?: number;
  /** Auto-layout: the space claimed by this card and its dependency cluster. */
  boundingBox?: {
    topLeft: XYPosition;
    bottomRight: XYPosition;
  };
  canManage: boolean;
  onSelectedInject(inject?: InjectOutputType): void;
  onCreate: (result: {
    result: string;
    entities: { injects: Record<string, InjectStore> };
  }) => void;
  onUpdate: (result: {
    result: string;
    entities: { injects: Record<string, InjectStore> };
  }) => void;
  onDelete: (result: string) => void;
};

export type NodeInject = Node<InjectNodeData>;

/**
 * The redesigned inject card of the interactive timeline: framed inject icon,
 * two-line title, live trigger-time chip (updates while dragging), chaining
 * badge, targets summary and a status dot - all on design-system tokens.
 */
const TimelineInjectNodeComponent = ({ data, selected }: NodeProps<NodeInject>) => {
  const theme = useTheme();
  const { t, fld, ft } = useFormatter();
  const inject = data.inject;
  if (!inject) {
    return null;
  }

  const enabled = inject.inject_enabled;
  const ready = inject.inject_ready;
  const dimmed = !enabled;
  const chained = (inject.inject_depends_on ?? []).length > 0;

  // Absolute time when the timeline is anchored to a real start date,
  // otherwise time relative to the (scenario) origin.
  const triggerLabel = data.startDate !== undefined
    ? (() => {
        const date = moment.utc(data.startDate).add(inject.inject_depends_duration, 's').toDate();
        return `${fld(date)} - ${ft(date)}`;
      })()
    : formatRelativeTime(inject.inject_depends_duration);

  let statusColor = theme.palette.success.main;
  let statusLabel = t('Enabled');
  if (!enabled) {
    statusColor = theme.palette.text.disabled;
    statusLabel = t('Disabled');
  } else if (!ready) {
    statusColor = theme.palette.warning.main;
    statusLabel = t('Missing content');
  }

  const targetsPreview = data.targets.filter(target => !!target);
  const shownTargets = targetsPreview.slice(0, 2);
  const hiddenTargets = targetsPreview.length - shownTargets.length;

  const stopPropagation = (event: MouseEvent) => event.stopPropagation();

  return (
    <Box
      data-testid="timeline-inject-node"
      onClick={() => data.onSelectedInject(inject)}
      sx={{
        'position': 'relative',
        'width': NODE_WIDTH,
        'backgroundColor': theme.palette.background.paper,
        'border': `1px solid ${selected ? theme.palette.primary.main : theme.palette.divider}`,
        'borderRadius': 1,
        'boxShadow': selected
          ? `0 0 0 1px ${theme.palette.primary.main}, 0 4px 14px ${alpha(theme.palette.common.black, 0.35)}`
          : `0 1px 4px ${alpha(theme.palette.common.black, 0.25)}`,
        'transition': 'border-color .15s, box-shadow .15s, transform .15s',
        'cursor': 'pointer',
        '&:hover': {
          borderColor: alpha(theme.palette.primary.main, 0.6),
          boxShadow: `0 6px 16px ${alpha(theme.palette.common.black, 0.35)}`,
        },
        // Left accent bar carrying the enabled state.
        '&::before': {
          content: '""',
          position: 'absolute',
          top: -1,
          bottom: -1,
          left: -1,
          width: 3,
          borderTopLeftRadius: theme.shape.borderRadius,
          borderBottomLeftRadius: theme.shape.borderRadius,
          backgroundColor: enabled ? theme.palette.primary.main : theme.palette.text.disabled,
        },
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 1,
        padding: theme.spacing(1, 1, 0.75, 1.5),
        opacity: dimmed ? 0.4 : 1,
      }}
      >
        <Box sx={{
          width: 32,
          height: 32,
          flexShrink: 0,
          display: 'grid',
          placeItems: 'center',
          borderRadius: 1,
          backgroundColor: theme.palette.background.accent,
        }}
        >
          <InjectIcon
            isPayload={isNotEmptyField(inject.inject_injector_contract?.injector_contract_payload)}
            type={
              inject.inject_injector_contract?.injector_contract_payload
                ? inject.inject_injector_contract?.injector_contract_payload?.payload_collector_type
                || inject.inject_injector_contract?.injector_contract_payload?.payload_type
                : inject.inject_type
            }
          />
        </Box>
        <Tooltip title={inject.inject_title}>
          <Typography sx={{
            fontSize: 13,
            fontWeight: 600,
            lineHeight: 1.35,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'break-word',
            minHeight: 36,
            flexGrow: 1,
          }}
          >
            {inject.inject_title}
          </Typography>
        </Tooltip>
        {/* The popover must never bubble its click into the card (which opens
            the update drawer). */}
        <Box component="span" onClick={stopPropagation} sx={{ marginTop: -0.5 }}>
          <InjectPopover
            inject={inject}
            setSelectedInjectId={() => data.onSelectedInject(inject)}
            canBeTested={inject.inject_testable}
            onCreate={data.onCreate}
            onUpdate={data.onUpdate}
            onDelete={data.onDelete}
          />
        </Box>
      </Box>
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.5,
        padding: theme.spacing(0, 1, 0, 1.5),
        opacity: dimmed ? 0.4 : 1,
      }}
      >
        <Box sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 0.5,
          padding: theme.spacing(0.25, 0.75),
          borderRadius: 1,
          backgroundColor: alpha(theme.palette.primary.main, 0.08),
          border: `1px solid ${alpha(theme.palette.primary.main, 0.3)}`,
        }}
        >
          <ScheduleOutlined sx={{
            fontSize: 12,
            color: theme.palette.primary.main,
          }}
          />
          <Typography sx={{
            fontSize: 11,
            lineHeight: '16px',
            color: theme.palette.primary.main,
            fontVariantNumeric: 'tabular-nums',
            whiteSpace: 'nowrap',
          }}
          >
            {triggerLabel}
          </Typography>
        </Box>
        {chained && (
          <Tooltip title={t('Triggered by a parent inject')}>
            <AccountTreeOutlined sx={{
              fontSize: 14,
              color: theme.palette.text.secondary,
            }}
            />
          </Tooltip>
        )}
      </Box>
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        padding: theme.spacing(0.75, 1, 1, 1.5),
      }}
      >
        <Tooltip title={targetsPreview.join(', ')}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
            minWidth: 0,
            flexGrow: 1,
            opacity: dimmed ? 0.4 : 1,
          }}
          >
            <TrackChangesOutlined sx={{
              fontSize: 13,
              flexShrink: 0,
              color: theme.palette.text.secondary,
            }}
            />
            <Typography sx={{
              fontSize: 11,
              color: theme.palette.text.secondary,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
            >
              {targetsPreview.length > 0
                ? `${shownTargets.join(', ')}${hiddenTargets > 0 ? ` +${hiddenTargets}` : ''}`
                : t('No target')}
            </Typography>
          </Box>
        </Tooltip>
        <Tooltip title={statusLabel}>
          <Box sx={{
            width: 8,
            height: 8,
            flexShrink: 0,
            borderRadius: '50%',
            backgroundColor: statusColor,
            boxShadow: `0 0 6px ${alpha(statusColor, 0.7)}`,
          }}
          />
        </Tooltip>
      </Box>
      <Handle
        type="target"
        id={`target-${inject.inject_id}`}
        position={Position.Left}
        isConnectable={data.canManage}
        style={{
          width: 10,
          height: 10,
          backgroundColor: theme.palette.background.paper,
          border: `2px solid ${theme.palette.primary.main}`,
        }}
      />
      <Handle
        type="source"
        id={`source-${inject.inject_id}`}
        position={Position.Right}
        isConnectable={data.canManage}
        style={{
          width: 10,
          height: 10,
          backgroundColor: theme.palette.primary.main,
          border: `2px solid ${theme.palette.background.paper}`,
        }}
      />
    </Box>
  );
};

const TimelineInjectNode = memo(TimelineInjectNodeComponent);

export default TimelineInjectNode;
