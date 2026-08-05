import { AddOutlined, BoltOutlined, GpsFixedOutlined, MoreVert, OutputOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type MouseEvent, type ReactNode, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import ActionTypeIcon from '../ActionTypeIcon';
import NodePopover from '../chaining_flow/nodes/NodePopover';
import LogicNodeTooltip, { type TooltipRow } from '../chaining_flow/NodeTooltip';
import { formatConditionKeyLabel } from '../events/event-types';
import graphTooltipSlotProps from './graphTooltipSlotProps';

export interface GraphActionCardProps {
  id: string;
  title: string;
  description?: string;
  injectorType?: string;
  payloadType?: string;
  isPayload?: boolean;
  /** MITRE tactic this action belongs to (shown as a chip). */
  tacticLabel?: string;
  /** Finding/output types this action produces (feeds triggers). */
  outputTypes?: string[];
  targetCount?: number;
  triggerCount?: number;
  /** Emphasized when it produces/consumes the currently selected trigger. */
  highlighted?: boolean;
  /** Faded out when a trigger is selected and this card is off its path. */
  dimmed?: boolean;
  /** 1-based badge index in the selected trigger's data-flow path. */
  pathIndex?: number;
  readOnly?: boolean;
  onEdit?: (id: string) => void;
  onDelete?: (id: string) => void;
  /** Inline "+": add a trigger fed by this action's outputs (continue the chain). */
  onAddTrigger?: (id: string) => void;
}

/** 'openbas_implant' -> 'Openbas implant' */
const prettifyType = (value?: string): string =>
  (value ?? '').replace(/[_-]+/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).trim();

const MetaItem = ({ icon, label }: {
  icon: ReactNode;
  label: ReactNode;
}) => (
  <Box sx={{
    display: 'flex',
    alignItems: 'center',
    gap: 0.25,
    color: 'text.secondary',
    fontSize: '0.6875rem',
    lineHeight: 1,
  }}
  >
    {icon}
    <span>{label}</span>
  </Box>
);

/**
 * Card visual for an action (step) in the causal graph. Reuses the shared structured tooltip and the
 * action type icon; carries an inline "+" slot (hidden in read-only) to grow the chain to the right.
 */
const GraphActionCard = ({
  id,
  title,
  description,
  injectorType,
  payloadType,
  isPayload,
  tacticLabel,
  outputTypes = [],
  targetCount = 0,
  triggerCount = 0,
  highlighted = false,
  dimmed = false,
  pathIndex,
  readOnly = false,
  onEdit,
  onDelete,
  onAddTrigger,
}: GraphActionCardProps) => {
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

  const typeLabel = prettifyType(payloadType ?? injectorType) || t('Command');
  const displayTitle = title?.trim() || t('Untitled action');

  const tooltipRows: TooltipRow[] = [{
    label: t('Type'),
    value: typeLabel,
  }];
  if (tacticLabel) tooltipRows.push({
    label: t('Tactic'),
    value: tacticLabel,
  });
  tooltipRows.push({
    label: t('Targets'),
    value: targetCount > 0 ? targetCount : t('Not scoped yet'),
  });
  if (triggerCount > 0) tooltipRows.push({
    label: t('Waits on'),
    value: `${triggerCount} ${t('trigger(s)')}`,
  });

  const tooltip = (
    <LogicNodeTooltip
      eyebrow={typeLabel}
      title={displayTitle}
      description={description}
      rows={tooltipRows}
      chips={outputTypes.map(formatConditionKeyLabel)}
    />
  );

  return (
    <Tooltip title={tooltip} placement="top" arrow disableInteractive enterDelay={300} slotProps={graphTooltipSlotProps}>
      <Box
        sx={{
          'position': 'relative',
          'width': '100%',
          'height': '100%',
          'display': 'flex',
          'flexDirection': 'column',
          'gap': 0.5,
          'padding': 1,
          'borderRadius': 1,
          'cursor': readOnly ? 'pointer' : 'grab',
          'opacity': dimmed ? 0.32 : 1,
          'border': `1px solid ${highlighted ? theme.palette.primary.main : theme.palette.divider}`,
          'backgroundColor': theme.palette.background.paper,
          'boxShadow': highlighted
            ? `0 0 0 1px ${theme.palette.primary.main}, ${theme.shadows[4]}`
            : theme.shadows[1],
          'transition': 'opacity 0.2s ease, border-color 0.15s ease, box-shadow 0.15s ease',
          '&:hover': {
            borderColor: theme.palette.primary.main,
            // Crisp 1px ring on all four sides (matching the selected state) so hovering reads as a
            // full outline, not a soft one-shadow glow.
            boxShadow: `0 0 0 1px ${theme.palette.primary.main}, ${theme.shadows[4]}`,
          },
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
            background: theme.palette.primary.main,
            color: theme.palette.primary.contrastText,
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
          gap: 1,
        }}
        >
          <Box sx={{
            'display': 'flex',
            'alignItems': 'center',
            'justifyContent': 'center',
            'width': 26,
            'height': 26,
            'flexShrink': 0,
            'borderRadius': 0.75,
            'overflow': 'hidden',
            // A broken collector/injector image would otherwise paint its alt text (e.g.
            // "openaev_netexec") at full size and spill it across the canvas: clip the box and
            // zero out any fallback text so only the 20px glyph (or nothing) can ever show.
            'fontSize': 0,
            'lineHeight': 0,
            'color': 'transparent',
            'backgroundColor': theme.palette.action.hover,
            '& img': {
              maxWidth: '100%',
              maxHeight: '100%',
              objectFit: 'contain',
            },
          }}
          >
            <ActionTypeIcon injectorType={injectorType} payloadType={payloadType} isPayload={isPayload} />
          </Box>
          <Typography
            variant="caption"
            sx={{
              flexGrow: 1,
              minWidth: 0,
              color: 'text.secondary',
              fontSize: '0.625rem',
              fontWeight: 700,
              letterSpacing: '0.05em',
              textTransform: 'uppercase',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              userSelect: 'none',
            }}
          >
            {typeLabel}
          </Typography>
          {tacticLabel && (
            <Box
              component="span"
              sx={{
                flexShrink: 0,
                maxWidth: 96,
                fontSize: '0.5625rem',
                fontWeight: 700,
                letterSpacing: '0.03em',
                textTransform: 'uppercase',
                color: theme.palette.primary.main,
                backgroundColor: `${theme.palette.primary.main}1f`,
                borderRadius: 0.5,
                paddingInline: 0.5,
                paddingBlock: '1px',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {tacticLabel}
            </Box>
          )}
          {!readOnly && (
            <IconButton
              size="small"
              sx={{ padding: 0.25 }}
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
        </Box>

        <Typography
          sx={{
            fontSize: '0.8125rem',
            fontWeight: 600,
            lineHeight: 1.25,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'break-word',
          }}
        >
          {displayTitle}
        </Typography>

        {(targetCount > 0 || triggerCount > 0 || outputTypes.length > 0) && (
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 1,
            marginTop: 'auto',
          }}
          >
            {targetCount > 0 && (
              <MetaItem
                icon={<GpsFixedOutlined sx={{ fontSize: 13 }} />}
                label={`${targetCount} ${t('target(s)')}`}
              />
            )}
            {triggerCount > 0 && (
              <MetaItem
                icon={<BoltOutlined sx={{ fontSize: 13 }} />}
                label={`${triggerCount} ${t('trigger(s)')}`}
              />
            )}
            {outputTypes.length > 0 && (
              <MetaItem
                icon={<OutputOutlined sx={{ fontSize: 13 }} />}
                label={`${outputTypes.length} ${t('output(s)')}`}
              />
            )}
          </Box>
        )}

        {/* No action-initiated connect handle: an action can never be manually linked to an event.
            The only action→event relationship is the automatic, informational inferred edge (this
            action produces output an event listens on); gating is created the other way, by dragging
            from a trigger/event onto an action (see GraphTriggerCard). */}

        {!readOnly && onAddTrigger && (
          <Tooltip title={t('Add a trigger fed by this action')} slotProps={graphTooltipSlotProps}>
            <IconButton
              size="small"
              onPointerDown={e => e.stopPropagation()}
              onClick={(e) => {
                e.stopPropagation();
                onAddTrigger(id);
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

export default GraphActionCard;
