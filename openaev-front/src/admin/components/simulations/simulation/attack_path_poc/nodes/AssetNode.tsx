import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import attackPathStatusColor from '../attack-path-poc-colors';
import { type AttackPathFlowNode } from '../attack-path-poc-flow-helpers';

export const AP_ENDPOINT_SIZE = 96;

// The endpoint (target) node: a circle whose ring is the prevention/detection colour. An endpoint
// with no findings is a faint dashed grey circle; one with findings carries a coloured "+N" badge of
// its distinct-finding count (collapsed mode). Mirrors the product mockup's endpoint node.
const AssetNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  // findingCounts is only set in collapsed mode; when it is present and empty the endpoint is known
  // to have no findings (faint dashed grey). Otherwise the ring follows the prevention status.
  const counts = data.findingCounts;
  const total = counts ? Object.values(counts).reduce((sum, n) => sum + n, 0) : 0;
  const knownNoFindings = counts !== undefined && total === 0;
  const color = knownNoFindings ? theme.palette.text.disabled : attackPathStatusColor(theme, data.status);
  return (
    <div style={{
      position: 'relative',
      width: AP_ENDPOINT_SIZE,
      height: AP_ENDPOINT_SIZE,
    }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <div
        style={{
          width: AP_ENDPOINT_SIZE,
          height: AP_ENDPOINT_SIZE,
          borderRadius: '50%',
          border: `2px ${knownNoFindings ? 'dashed' : 'solid'} ${color}`,
          background: selected ? alpha(color, 0.12) : theme.palette.background.paper,
          boxShadow: selected ? `0 0 0 3px ${alpha(color, 0.4)}` : 'none',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 4,
          boxSizing: 'border-box',
        }}
      >
        <Typography
          variant="caption"
          fontWeight={700}
          sx={{
            maxWidth: AP_ENDPOINT_SIZE - 12,
            textAlign: 'center',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            lineHeight: 1.1,
          }}
        >
          {data.label}
        </Typography>
        {data.ip && (
          <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>
            {data.ip}
          </Typography>
        )}
      </div>
      {total > 0 && (
        <div
          style={{
            position: 'absolute',
            top: -6,
            right: -6,
            minWidth: 22,
            height: 22,
            padding: '0 6px',
            borderRadius: 11,
            background: color,
            color: theme.palette.getContrastText(color),
            fontSize: 11,
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {`+${total}`}
        </div>
      )}
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(AssetNode);
