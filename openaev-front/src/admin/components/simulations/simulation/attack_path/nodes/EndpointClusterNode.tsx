import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import attackPathStatusColor, { attackPathStatusLabel } from '../attack-path-colors';
import { type AttackPathFlowNode } from '../attack-path-flow-helpers';
import { AP_ENDPOINT_CLUSTER_SIZE } from './node-sizes';

// An aggregate endpoint cluster. Two roles: the "header" ("+N" of an injector, click to expand /
// collapse its endpoints) and an "overflow" ("+rest", click to reveal the next batch). Its ring
// colour reflects the aggregated prevention status of the endpoints it stands for (green all
// prevented, orange partial, red none). Clicking is handled by the page.
const EndpointClusterNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const isOverflow = data.clusterKind === 'overflow';
  const expanded = data.expanded ?? false;
  const color = selected ? theme.palette.primary.main : attackPathStatusColor(theme, data.status);
  let sub = t('Endpoints');
  if (isOverflow) {
    sub = t('More');
  } else if (expanded) {
    sub = t('Collapse');
  }
  // Aggregated verdict as text so status is never colour-alone (a11y).
  const statusText = t(attackPathStatusLabel(data.status));
  return (
    <div
      style={{
        width: AP_ENDPOINT_CLUSTER_SIZE,
        height: AP_ENDPOINT_CLUSTER_SIZE,
        borderRadius: '50%',
        border: `${selected ? 3 : 2}px ${expanded && !isOverflow ? 'solid' : 'dashed'} ${color}`,
        background: theme.palette.background.paper,
        boxShadow: selected ? `0 0 0 4px ${alpha(color, 0.35)}` : 'none',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: 'pointer',
      }}
      title={`+${data.count ?? 0} ${sub} — ${statusText}`}
      aria-label={`+${data.count ?? 0} ${sub}, ${statusText}`}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Typography variant="h6" fontWeight={700} sx={{ lineHeight: 1 }}>
        {`+${data.count ?? 0}`}
      </Typography>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{
          fontSize: 9,
          letterSpacing: 1,
          textTransform: 'uppercase',
        }}
      >
        {sub}
      </Typography>
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(EndpointClusterNode);
