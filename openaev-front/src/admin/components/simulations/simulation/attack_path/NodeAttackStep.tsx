import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { FunctionComponent } from 'react';

import { STATUS_COLORS, type NodeAttackStepData } from './AttackPathFlow';

const NODE_W = 200;
const NODE_H = 56;
const CIRCLE_R = 28;

const NodeAttackStep: FunctionComponent<NodeProps> = ({ id, data }) => {
  const theme = useTheme();
  const d = data as NodeAttackStepData;
  const colors = STATUS_COLORS[d.status];
  const dimmed = d.highlightState === 'dimmed';
  const isSource = d.highlightState === 'source';

  return (
    <Box
      onClick={() => d.onClick(id)}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        cursor: 'pointer',
        opacity: dimmed ? 0.2 : 1,
        transition: 'opacity 0.2s',
      }}
    >
      {/* Status circle */}
      <Box
        sx={{
          width: CIRCLE_R * 2,
          height: CIRCLE_R * 2,
          borderRadius: '50%',
          backgroundColor: colors.fill,
          border: isSource ? `3px solid ${theme.palette.common.white}` : `2px solid ${colors.stroke}`,
          boxShadow: isSource ? `0 0 8px ${colors.fill}` : 'none',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        {d.sequenceNumber != null && (
          <Typography
            variant="body2"
            sx={{
              color: '#fff',
              fontWeight: 700,
              fontSize: 14,
            }}
          >
            {d.sequenceNumber}
          </Typography>
        )}
      </Box>

      {/* Label */}
      <Box sx={{ minWidth: 0, maxWidth: NODE_W - CIRCLE_R * 2 - 16 }}>
        <Typography
          variant="body2"
          noWrap
          sx={{
            color: theme.palette.text.primary,
            fontWeight: 600,
            fontSize: 13,
            lineHeight: 1.2,
          }}
        >
          {d.label}
        </Typography>
        {d.assetName && (
          <Typography
            variant="caption"
            noWrap
            sx={{ color: theme.palette.text.secondary, fontSize: 11 }}
          >
            {d.assetName}
          </Typography>
        )}
      </Box>

      <Handle type="target" position={Position.Left} style={{ visibility: 'hidden' }} />
      <Handle type="source" position={Position.Right} style={{ visibility: 'hidden' }} />
    </Box>
  );
};

export default NodeAttackStep;
