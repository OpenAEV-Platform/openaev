import { Add, BoltOutlined } from '@mui/icons-material';
import { Button, Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import ButtonPopover from '../../../../../../components/common/ButtonPopover';
import { useFormatter } from '../../../../../../components/i18n';
import type { WorkflowCondition, WorkflowStep } from '../../../../../../utils/api-types-custom';
import type { HighlightState } from './NodeAction';

export type NodeEventData = {
  step: WorkflowStep;
  label: string;
  fieldConditions: WorkflowCondition[];
  highlightState: HighlightState;
  onEdit: (step: WorkflowStep) => void;
  onDelete: (stepId: string) => void;
  onAddAction: (eventStepId: string) => void;
  onHighlight: (stepId: string) => void;
};

export type NodeEventType = Node<NodeEventData>;

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

  const borderStyle = hl === 'source'
    ? { border: `3px solid ${theme.palette.warning.main}`, boxShadow: theme.shadows[6] }
    : hl === 'highlighted'
      ? { border: `2px solid ${theme.palette.warning.light}`, boxShadow: theme.shadows[3] }
      : { border: `1px solid ${theme.palette.divider}` };

  return (
    <div
      onClick={() => data.onHighlight(data.step.step_id)}
      style={{
        width: 280,
        opacity: isDimmed ? 0.3 : 1,
        transition: 'opacity 0.2s, border 0.2s, box-shadow 0.2s',
        cursor: 'pointer',
      }}
    >
      <div
        style={{
          background: theme.palette.background.paper,
          ...borderStyle,
          borderLeft: `4px solid ${theme.palette.warning.main}`,
          borderRadius: 4,
          padding: '10px 12px',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
        }}
      >
        <Handle type="target" position={Position.Top} style={{ visibility: 'hidden' }} />
        <BoltOutlined sx={{ color: theme.palette.warning.main, fontSize: 20 }} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Typography variant="body2" noWrap fontWeight="bold" sx={{ maxWidth: 140 }}>
              {data.label}
            </Typography>
            <Chip size="small" label={t('Event')} color="warning" variant="outlined" sx={{ height: 18, fontSize: 10 }} />
          </div>
          {data.fieldConditions.length > 0 && (
            <div style={{ display: 'flex', gap: 4, marginTop: 4, flexWrap: 'wrap' }}>
              {data.fieldConditions.map(condition => (
                <Chip
                  key={condition.condition_id}
                  size="small"
                  variant="outlined"
                  label={`${condition.condition_key}${condition.condition_field ? `.${condition.condition_field}` : ''} ${condition.condition_type}${condition.condition_value ? ` ${condition.condition_value}` : ''}`}
                  sx={{ height: 18, fontSize: 10 }}
                />
              ))}
            </div>
          )}
        </div>
        <div className="nopan nodrag">
          <ButtonPopover entries={popoverEntries} variant="icon" size="small" />
        </div>
        <Handle type="source" position={Position.Bottom} style={{ visibility: 'hidden' }} />
      </div>
      <div className="nopan nodrag" style={{ display: 'flex', justifyContent: 'center', padding: '4px 0' }}>
        <Button
          size="small"
          startIcon={<Add />}
          onClick={() => data.onAddAction(data.step.step_id)}
          sx={{ textTransform: 'none', fontSize: 11 }}
        >
          {t('Add Action')}
        </Button>
      </div>
    </div>
  );
};

export default memo(NodeEvent);
