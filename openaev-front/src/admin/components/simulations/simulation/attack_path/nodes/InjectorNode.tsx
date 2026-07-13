import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { type AttackPathFlowNode } from '../attack-path-flow-helpers';

export const AP_INJECTOR_WIDTH = 150;

// The injector (source) node: a hexagon with the injector name and an INJECTOR sublabel, the source
// of the execution edges. Mirrors the product mockup's injector node.
const InjectorNode = ({ data }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  return (
    <div
      style={{
        width: AP_INJECTOR_WIDTH,
        height: 64,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: theme.palette.background.paper,
        border: `1px solid ${theme.palette.text.secondary}`,
        clipPath: 'polygon(14% 0, 86% 0, 100% 50%, 86% 100%, 14% 100%, 0 50%)',
      }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Typography
        variant="body2"
        fontWeight={700}
        sx={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          maxWidth: AP_INJECTOR_WIDTH - 40,
        }}
      >
        {data.label}
      </Typography>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{
          fontSize: 9,
          letterSpacing: 1,
        }}
      >
        INJECTOR
      </Typography>
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(InjectorNode);
