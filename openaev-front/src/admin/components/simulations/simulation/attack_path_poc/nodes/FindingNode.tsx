import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { type AttackPathFlowNode } from '../attack-path-poc-flow-helpers';
import { AP_NODE_WIDTH } from './InjectorNode';

// A leaf finding node: the discovered value (a credential, a CVE id, a port, ...).
const FindingNode = ({ data }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  return (
    <div
      style={{
        width: AP_NODE_WIDTH * 0.75,
        borderRadius: theme.spacing(2),
        border: `1px solid ${theme.palette.divider}`,
        padding: theme.spacing(0.5, 1.5),
        background: theme.palette.background.default,
      }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Typography
        variant="caption"
        sx={{
          display: 'block',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {data.label}
      </Typography>
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(FindingNode);
