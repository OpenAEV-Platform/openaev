import { Add, BoltOutlined } from '@mui/icons-material';
import { Chip, IconButton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import ButtonPopover from '../../../../../../components/common/ButtonPopover';
import FindingIcon from '../../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../../components/i18n';
import type { WorkflowCondition, WorkflowStep } from '../../../../../../utils/api-types-custom';
import type { HighlightState } from './NodeAction';

export type NodeEventData = {
  step: WorkflowStep;
  label: string;
  fieldConditions: WorkflowCondition[];
  flowTypes: string[];
  highlightState: HighlightState;
  sequenceNumber?: number;
  onEdit: (step: WorkflowStep) => void;
  onDelete: (stepId: string) => void;
  onAddAction: (eventStepId: string) => void;
  onHighlight: (stepId: string) => void;
};

export type NodeEventType = Node<NodeEventData>;

const CIRCLE_SIZE = 100;

const NodeEvent = ({ data }: NodeProps<NodeEventType>) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const popoverEntries = [
    {
      label: t('Edit'),
      action: () => data.onEdit(data.step),
      userRight: true,
    },
    {
      label: t('Delete'),
      action: () => data.onDelete(data.step.step_id),
      userRight: true,
    },
  ];

  const hl = data.highlightState;
  const isDimmed = hl === 'dimmed';

  const borderColor = hl === 'source'
    ? theme.palette.warning.main
    : hl === 'highlighted'
      ? theme.palette.warning.light
      : theme.palette.divider;

  const shadowStyle = hl === 'source'
    ? theme.shadows[6]
    : hl === 'highlighted'
      ? theme.shadows[3]
      : 'none';

  return (
    <div
      onClick={() => data.onHighlight(data.step.step_id)}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        opacity: isDimmed ? 0.3 : 1,
        transition: 'opacity 0.2s',
        cursor: 'pointer',
        position: 'relative',
      }}
    >
      <Handle type="target" position={Position.Left} style={{ left: 0, top: CIRCLE_SIZE / 2, visibility: 'hidden' }} />
      <Handle type="source" position={Position.Right} style={{ left: CIRCLE_SIZE, top: CIRCLE_SIZE / 2, visibility: 'hidden' }} />
      {/* Sequence badge */}
      {data.sequenceNumber != null && (
        <div style={{
          position: 'absolute',
          top: -4,
          right: -4,
          width: 22,
          height: 22,
          borderRadius: '50%',
          background: theme.palette.warning.main,
          color: theme.palette.warning.contrastText,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 11,
          fontWeight: 700,
          boxShadow: theme.shadows[2],
          zIndex: 2,
        }}>
          {data.sequenceNumber}
        </div>
      )}
      {/* Circle shape */}
      <div style={{
        width: CIRCLE_SIZE,
        height: CIRCLE_SIZE,
        borderRadius: '50%',
        background: theme.palette.background.paper,
        border: `2px solid ${borderColor}`,
        boxShadow: shadowStyle,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 8,
        overflow: 'hidden',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <BoltOutlined sx={{ color: theme.palette.warning.main, fontSize: 14, flexShrink: 0 }} />
          <Typography
            fontWeight="bold"
            sx={{ fontSize: 11, lineHeight: 1.2, textAlign: 'center', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: CIRCLE_SIZE - 40 }}
          >
            {data.label}
          </Typography>
          <div className="nopan nodrag" style={{ flexShrink: 0 }}>
            <ButtonPopover entries={popoverEntries} variant="icon" size="small" />
          </div>
        </div>
        {/* Conditions inside circle (compact) */}
        {data.fieldConditions.length > 0 && (() => {
          const groups = new Map<string, { key: string; field?: string; entries: { type: string; value?: string }[] }>();
          for (const c of data.fieldConditions) {
            const groupKey = `${c.condition_key ?? ''}|${c.condition_field ?? ''}`;
            if (!groups.has(groupKey)) {
              groups.set(groupKey, { key: c.condition_key ?? '', field: c.condition_field ?? undefined, entries: [] });
            }
            groups.get(groupKey)!.entries.push({ type: c.condition_type ?? '=', value: c.condition_value ?? undefined });
          }
          return (
            <Tooltip title={[...groups.values()].map(g => `${g.key}${g.field ? `.${g.field}` : ''} ${g.entries.map(e => `${e.type} ${e.value ?? ''}`).join(' or ')}`).join(', ')}>
              <Typography
                sx={{ fontSize: 9, color: theme.palette.text.secondary, textAlign: 'center', lineHeight: 1.1, overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: CIRCLE_SIZE - 24, whiteSpace: 'nowrap' }}
              >
                {[...groups.values()].map(g => {
                  const fieldPart = g.field ?? g.key;
                  const valuesPart = g.entries.map(e => e.value ?? e.type).join(' or ');
                  return `${fieldPart}=${valuesPart}`;
                }).join(', ')}
              </Typography>
            </Tooltip>
          );
        })()}
      </div>
      {/* Flow type chips below circle */}
      {(() => {
        const conditionKeys = new Set(data.fieldConditions.map(c => c.condition_key).filter(Boolean));
        const extraFlowTypes = data.flowTypes.filter(ft => !conditionKeys.has(ft));
        return extraFlowTypes.length > 0 && (
          <div style={{ display: 'flex', gap: 3, flexWrap: 'wrap', justifyContent: 'center', marginTop: 4, maxWidth: CIRCLE_SIZE + 60 }}>
            {extraFlowTypes.map(type => (
              <Chip
                key={type}
                size="small"
                icon={<FindingIcon findingType={type} />}
                label={type}
                variant="outlined"
                color="warning"
                sx={{ height: 20, fontSize: 10 }}
              />
            ))}
          </div>
        );
      })()}
      {/* Blue "Add Action" dot */}
      <div className="nopan nodrag" style={{ marginTop: 4, display: 'flex', justifyContent: 'center' }}>
        <Tooltip title={t('Add Action')}>
          <IconButton
            size="small"
            onClick={(e) => { e.stopPropagation(); data.onAddAction(data.step.step_id); }}
            sx={{
              width: 26,
              height: 26,
              background: theme.palette.primary.main,
              color: theme.palette.primary.contrastText,
              '&:hover': { background: theme.palette.primary.dark },
              boxShadow: theme.shadows[2],
            }}
          >
            <Add sx={{ fontSize: 16 }} />
          </IconButton>
        </Tooltip>
      </div>
    </div>
  );
};

export default memo(NodeEvent);
