import { LocalFireDepartment } from '@mui/icons-material';
import { Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import attackPathStatusColor, { attackPathChokepointColor, attackPathStatusLabel } from '../attack-path-colors';
import { type AttackPathFlowNode } from '../attack-path-flow-helpers';
import { AP_ENDPOINT_SIZE } from './node-sizes';

// The endpoint (target) node: a circle whose ring is the prevention/detection colour. An endpoint
// with no findings is a faint dashed grey circle; one with findings carries a coloured "+N" badge of
// its distinct-finding count (collapsed mode). Mirrors the product mockup's endpoint node.
const AssetNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  // findingCounts is only set in collapsed mode; when it is present and empty the endpoint is known
  // to have no findings (faint dashed grey). Otherwise the ring follows the prevention status.
  const counts = data.findingCounts;
  const total = counts ? Object.values(counts).reduce((sum, n) => sum + n, 0) : 0;
  const knownNoFindings = counts !== undefined && total === 0;
  const color = knownNoFindings ? theme.palette.text.disabled : attackPathStatusColor(theme, data.status);
  const isChokepoint = data.chokepointRank !== undefined;
  const chokepointColor = attackPathChokepointColor(theme);
  // Ring stays the verdict colour; the chokepoint signal is a separate violet halo + badge so a single
  // colour never carries two meanings. Status is also exposed as text below (a11y).
  const statusText = knownNoFindings ? t('No findings') : t(attackPathStatusLabel(data.status));
  let nodeShadow = 'none';
  if (isChokepoint) {
    nodeShadow = `0 0 0 6px ${alpha(chokepointColor, 0.3)}`;
  } else if (selected) {
    nodeShadow = `0 0 0 4px ${alpha(color, 0.45)}`;
  }
  return (
    <div
      style={{
        position: 'relative',
        width: AP_ENDPOINT_SIZE,
        height: AP_ENDPOINT_SIZE,
      }}
      title={`${data.label} — ${statusText}`}
      aria-label={`${data.label}, ${statusText}${isChokepoint ? `, ${t('chokepoint')}` : ''}`}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <div
        style={{
          width: AP_ENDPOINT_SIZE,
          height: AP_ENDPOINT_SIZE,
          borderRadius: '50%',
          border: `${selected || isChokepoint ? 3 : 2}px ${knownNoFindings ? 'dashed' : 'solid'} ${color}`,
          // Keep the dark node fill (readable white label); show selection with a halo ring, not a
          // pale fill that would wash the text out. A chokepoint gets a violet halo so it stands out
          // without overriding the verdict-coloured ring.
          background: theme.palette.background.paper,
          boxShadow: nodeShadow,
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
      {data.chokepointRank !== undefined && (
        <Tooltip title={t('Chokepoint #{rank} — most exposed endpoint ({count} findings)', {
          rank: String(data.chokepointRank),
          count: String(total),
        })}
        >
          <div
            style={{
              position: 'absolute',
              top: -12,
              left: -12,
              minWidth: 34,
              height: 30,
              padding: '0 9px',
              borderRadius: 15,
              background: chokepointColor,
              color: theme.palette.getContrastText(chokepointColor),
              fontSize: 15,
              fontWeight: 800,
              display: 'flex',
              alignItems: 'center',
              gap: 3,
              justifyContent: 'center',
              boxShadow: `0 0 0 3px ${theme.palette.background.paper}, 0 2px 6px ${alpha(theme.palette.common.black, 0.4)}`,
            }}
          >
            <LocalFireDepartment sx={{ fontSize: 19 }} />
            {data.chokepointRank}
          </div>
        </Tooltip>
      )}
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
