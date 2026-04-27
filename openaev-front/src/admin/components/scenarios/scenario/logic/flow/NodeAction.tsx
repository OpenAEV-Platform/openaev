import { PlayArrowOutlined } from '@mui/icons-material';
import { Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import ButtonPopover from '../../../../../../components/common/ButtonPopover';
import { useFormatter } from '../../../../../../components/i18n';
import type { WorkflowStep } from '../../../../../../utils/api-types-custom';
import type { InputBinding } from '../logicUtils';
import InjectIcon from '../../../../common/injects/InjectIcon';

export type HighlightState = 'source' | 'highlighted' | 'dimmed' | null;

export type NodeActionData = {
  step: WorkflowStep;
  label: string;
  injectorType: string | null;
  attackPatternExternalIds: string[];
  outputTypes: string[];
  inputBindings: InputBinding[];
  fieldScopes: Record<string, string>;
  hasParentEvent: boolean;
  highlightState: HighlightState;
  sequenceNumber?: number;
  onEdit: (step: WorkflowStep) => void;
  onDelete: (stepId: string) => void;
  onHighlight: (stepId: string) => void;
};

export type NodeActionType = Node<NodeActionData>;

const NodeAction = ({ data }: NodeProps<NodeActionType>) => {
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
    ? { border: `3px solid ${theme.palette.primary.main}`, boxShadow: theme.shadows[6] }
    : hl === 'highlighted'
      ? { border: `2px solid ${theme.palette.primary.light}`, boxShadow: theme.shadows[3] }
      : { border: `1px solid ${theme.palette.divider}` };

  return (
    <div
      onClick={() => data.onHighlight(data.step.step_id)}
      style={{
        width: 280,
        background: theme.palette.background.paper,
        ...borderStyle,
        borderLeft: hl === 'source'
          ? `4px solid ${theme.palette.primary.main}`
          : hl === 'highlighted'
            ? `4px solid ${theme.palette.primary.light}`
            : `4px solid ${theme.palette.primary.main}`,
        borderRadius: 4,
        padding: '10px 12px',
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        opacity: isDimmed ? 0.3 : 1,
        transition: 'opacity 0.2s, border 0.2s, box-shadow 0.2s',
        cursor: 'pointer',
        position: 'relative',
      }}
    >
      {data.sequenceNumber != null && (
        <div style={{
          position: 'absolute',
          top: -10,
          right: -10,
          width: 22,
          height: 22,
          borderRadius: '50%',
          background: theme.palette.primary.main,
          color: theme.palette.primary.contrastText,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 11,
          fontWeight: 700,
          boxShadow: theme.shadows[2],
          zIndex: 1,
        }}>
          {data.sequenceNumber}
        </div>
      )}
      <Handle type="target" position={Position.Left} style={{ visibility: 'hidden' }} />
      {data.injectorType
        ? <InjectIcon type={data.injectorType} size="small" tooltip={{}} />
        : <PlayArrowOutlined color="primary" sx={{ fontSize: 20 }} />
      }
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <Typography variant="body2" noWrap fontWeight="bold" sx={{ maxWidth: 160 }}>
            {data.label}
          </Typography>
          <Chip size="small" label={t('Action')} color="primary" variant="outlined" sx={{ height: 18, fontSize: 10 }} />
          {data.attackPatternExternalIds.map(eid => (
            <Chip key={eid} size="small" label={eid} variant="outlined" color="secondary" sx={{ height: 18, fontSize: 10 }} />
          ))}
        </div>
      </div>
      <div className="nopan nodrag">
        <ButtonPopover entries={popoverEntries} variant="icon" size="small" />
      </div>
      <Handle type="source" position={Position.Right} style={{ visibility: 'hidden' }} />
    </div>
  );
};

export default memo(NodeAction);
