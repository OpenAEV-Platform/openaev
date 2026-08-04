import { AddOutlined, BoltOutlined, MoreVert } from '@mui/icons-material';
import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type MouseEvent, type PointerEvent as ReactPointerEvent, type ReactNode, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import NodePopover from '../chaining_flow/nodes/NodePopover';
import LogicNodeTooltip, { type TooltipRow } from '../chaining_flow/NodeTooltip';
import { formatConditionKeyLabel } from '../events/event-types';
import graphTooltipSlotProps from './graphTooltipSlotProps';

export interface GraphTriggerCardProps {
  id: string;
  name?: string;
  description?: string;
  /** Distinct condition fields the trigger listens on (e.g. ["credentials", "hostname"]). */
  conditionFields?: string[];
  /** Human-readable one-liner per condition (e.g. `Hostname contains "dc01"`). */
  conditionLines?: string[];
  conditionOperator?: 'AND' | 'OR';
  /** Whether this trigger is currently selected (reveals its data-flow spotlight). */
  selected?: boolean;
  highlighted?: boolean;
  dimmed?: boolean;
  pathIndex?: number;
  readOnly?: boolean;
  onEdit?: (id: string) => void;
  onDelete?: (id: string) => void;
  /** Inline "+": add an action gated by this trigger (continue the chain). */
  onAddAction?: (id: string) => void;
  /** Drag from the right handle onto an existing action to gate that action by this trigger. */
  onConnectStart?: (id: string, kind: 'trigger', event: ReactPointerEvent<HTMLElement>) => void;
}

/**
 * Card visual for a trigger (event) in the causal graph. The orchestrator often leaves triggers
 * unnamed, so the title falls back to the listened-on fields. Reuses the shared structured tooltip
 * and carries an inline "+" slot (hidden in read-only) to add a gated action.
 */
const GraphTriggerCard = ({
  id,
  name,
  description,
  conditionFields = [],
  conditionLines = [],
  conditionOperator = 'AND',
  selected = false,
  highlighted = false,
  dimmed = false,
  pathIndex,
  readOnly = false,
  onEdit,
  onDelete,
  onAddAction,
  onConnectStart,
}: GraphTriggerCardProps) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const handleMenuOpen = (e: MouseEvent<HTMLElement>) => {
    e.stopPropagation();
    setAnchorEl(e.currentTarget);
  };
  const handleMenuClose = () => setAnchorEl(null);
  const handleEdit = () => {
    handleMenuClose();
    onEdit?.(id);
  };
  const handleDelete = () => {
    handleMenuClose();
    onDelete?.(id);
  };

  const trimmedName = (name ?? '').trim();
  const title = trimmedName
    || (conditionFields.length > 0
      ? conditionFields.map(formatConditionKeyLabel).join(', ')
      : t('Trigger'));

  let summaryLine: ReactNode = t('Waits for a matching finding');
  if (conditionLines.length === 1) {
    summaryLine = conditionLines[0];
  } else if (conditionLines.length > 1) {
    summaryLine = `${conditionLines[0]} (+${conditionLines.length - 1})`;
  }

  const tooltipRows: TooltipRow[] = [];
  if (conditionLines.length > 0) {
    tooltipRows.push({
      label: t('When'),
      value: conditionLines.join(` ${conditionOperator} `),
    });
  }
  if (conditionFields.length > 0) {
    tooltipRows.push({
      label: t('Listens on'),
      value: conditionFields.map(formatConditionKeyLabel).join(', '),
    });
  }

  const tooltip = (
    <LogicNodeTooltip
      eyebrow={t('Trigger')}
      title={title}
      description={description}
      rows={tooltipRows}
      chips={conditionFields.map(formatConditionKeyLabel)}
      accentColor={theme.palette.warning.main}
    />
  );

  const active = selected || highlighted;

  return (
    <Tooltip title={tooltip} placement="top" arrow disableInteractive enterDelay={300} slotProps={graphTooltipSlotProps}>
      <Box
        sx={{
          'position': 'relative',
          'width': '100%',
          'height': '100%',
          'display': 'flex',
          'alignItems': 'center',
          'gap': 1,
          'padding': 1,
          'borderRadius': 1,
          'cursor': readOnly ? 'pointer' : 'grab',
          'opacity': dimmed ? 0.32 : 1,
          'border': `1px solid ${active ? theme.palette.warning.main : theme.palette.divider}`,
          'borderLeft': `3px solid ${theme.palette.warning.main}`,
          'backgroundColor': theme.palette.background.paper,
          'boxShadow': active
            ? `0 0 0 1px ${theme.palette.warning.main}, ${theme.shadows[4]}`
            : theme.shadows[1],
          'transition': 'opacity 0.2s ease, border-color 0.15s ease, box-shadow 0.15s ease',
          '&:hover': { boxShadow: theme.shadows[4] },
        }}
      >
        {pathIndex !== undefined && (
          <Box sx={{
            position: 'absolute',
            top: -10,
            left: -10,
            width: 20,
            height: 20,
            borderRadius: '50%',
            background: theme.palette.warning.main,
            color: theme.palette.warning.contrastText,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 12,
            fontWeight: 700,
            zIndex: 2,
          }}
          >
            {pathIndex}
          </Box>
        )}

        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 26,
          height: 26,
          flexShrink: 0,
          borderRadius: '50%',
          backgroundColor: `${theme.palette.warning.main}1f`,
        }}
        >
          <BoltOutlined sx={{
            fontSize: 17,
            color: theme.palette.warning.main,
          }}
          />
        </Box>

        <Box sx={{
          minWidth: 0,
          flexGrow: 1,
        }}
        >
          <Typography
            sx={{
              fontSize: '0.8125rem',
              fontWeight: 600,
              lineHeight: 1.25,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {title}
          </Typography>
          <Typography
            sx={{
              fontSize: '0.6875rem',
              color: 'text.secondary',
              lineHeight: 1.3,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {summaryLine}
          </Typography>
        </Box>

        {!readOnly && (
          <IconButton
            size="small"
            sx={{ padding: 0.25 }}
            aria-haspopup="true"
            onPointerDown={e => e.stopPropagation()}
            onClick={handleMenuOpen}
          >
            <MoreVert sx={{
              fontSize: 18,
              color: theme.palette.primary.main,
            }}
            />
          </IconButton>
        )}

        {!readOnly && onConnectStart && (
          <Tooltip title={t('Drag onto an action to gate it with this trigger')} slotProps={graphTooltipSlotProps}>
            <Box
              onPointerDown={e => onConnectStart(id, 'trigger', e)}
              onClick={e => e.stopPropagation()}
              sx={{
                'position': 'absolute',
                'right': -11,
                'top': '50%',
                'transform': 'translateY(-50%)',
                'zIndex': 3,
                'width': 16,
                'height': 16,
                'borderRadius': '50%',
                'cursor': 'grab',
                'display': 'flex',
                'alignItems': 'center',
                'justifyContent': 'center',
                'backgroundColor': theme.palette.background.paper,
                'border': `2px solid ${theme.palette.warning.main}`,
                'boxShadow': theme.shadows[1],
                'touchAction': 'none',
                '&:hover': { backgroundColor: theme.palette.warning.main },
                '&:active': { cursor: 'grabbing' },
              }}
            >
              <Box sx={{
                width: 5,
                height: 5,
                borderRadius: '50%',
                backgroundColor: theme.palette.warning.main,
              }}
              />
            </Box>
          </Tooltip>
        )}

        {!readOnly && onAddAction && (
          <Tooltip title={t('Add an action gated by this trigger')} slotProps={graphTooltipSlotProps}>
            <IconButton
              size="small"
              onPointerDown={e => e.stopPropagation()}
              onClick={(e) => {
                e.stopPropagation();
                onAddAction(id);
              }}
              sx={{
                'position': 'absolute',
                'bottom': -13,
                'left': '50%',
                'transform': 'translateX(-50%)',
                'zIndex': 3,
                'width': 22,
                'height': 22,
                'padding': 0,
                'color': theme.palette.primary.contrastText,
                'backgroundColor': theme.palette.primary.main,
                'boxShadow': theme.shadows[2],
                '&:hover': { backgroundColor: theme.palette.primary.dark },
              }}
            >
              <AddOutlined sx={{ fontSize: 15 }} />
            </IconButton>
          </Tooltip>
        )}

        {!readOnly && (
          <NodePopover
            anchorEl={anchorEl}
            onClose={handleMenuClose}
            onEdit={handleEdit}
            onDelete={handleDelete}
          />
        )}
      </Box>
    </Tooltip>
  );
};

export default GraphTriggerCard;
