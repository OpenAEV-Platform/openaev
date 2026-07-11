import { Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import attackPathStatusColor from '../attack-path-poc-colors';
import { type AttackPathFlowNode } from '../attack-path-poc-flow-helpers';
import { AP_NODE_WIDTH } from './InjectorNode';

// The endpoint (target) node: a card whose left rail is the prevention colour. In collapsed mode it
// also shows the per-type finding counts, so a single node summarises what an expand would reveal.
const AssetNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  const color = attackPathStatusColor(theme, data.status);
  const counts = data.findingCounts ?? {};
  const countEntries = Object.entries(counts);
  return (
    <div
      style={{
        width: AP_NODE_WIDTH,
        borderRadius: theme.spacing(1),
        border: `1px solid ${selected ? theme.palette.primary.main : theme.palette.divider}`,
        borderLeft: `4px solid ${color}`,
        padding: theme.spacing(1, 1.5),
        background: theme.palette.background.paper,
      }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Typography
        variant="body2"
        fontWeight={600}
        sx={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {data.label}
      </Typography>
      {data.ip && (
        <Typography variant="caption" color="text.secondary">
          {data.ip}
          {data.platform ? ` · ${data.platform}` : ''}
        </Typography>
      )}
      {countEntries.length > 0 && (
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 4,
          marginTop: 4,
        }}
        >
          {countEntries.map(([type, n]) => (
            <Chip key={type} label={`${type} ${n}`} size="small" variant="outlined" />
          ))}
        </div>
      )}
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(AssetNode);
