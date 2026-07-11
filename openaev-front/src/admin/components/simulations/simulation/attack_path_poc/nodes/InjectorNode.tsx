import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { type AttackPathFlowNode } from '../attack-path-poc-flow-helpers';

export const AP_NODE_WIDTH = 240;

// The injector (source) node: a hexagon, echoing the attack-path widget's source shape.
const InjectorNode = ({ data }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  return (
    <div
      style={{
        width: AP_NODE_WIDTH,
        height: 56,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: theme.spacing(0, 3),
        background: theme.palette.background.paper,
        border: `1px solid ${theme.palette.primary.main}`,
        clipPath: 'polygon(12% 0, 88% 0, 100% 50%, 88% 100%, 12% 100%, 0 50%)',
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
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(InjectorNode);
