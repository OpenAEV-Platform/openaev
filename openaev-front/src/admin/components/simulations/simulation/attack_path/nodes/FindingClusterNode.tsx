import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import FindingIcon from '../../../../../../components/FindingIcon';
import attackPathStatusColor from '../attack-path-colors';
import { type AttackPathFlowNode } from '../attack-path-flow-helpers';

// An aggregate finding cluster. As a "header" (one per injector + finding type) it shows the type
// icon, the aggregated count and the finding type name below, and expands into individual findings on
// click; as an "overflow" it is the "+rest" batch loader. Clicking is handled by the page.
const FindingClusterNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  const isOverflow = data.clusterKind === 'overflow';
  const expanded = data.expanded ?? false;
  const active = selected || expanded;
  // Verdict colour (green/orange/red) by default; blue only when this node is the selected path.
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const color = selected ? theme.palette.primary.main : verdict;
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 3,
      cursor: 'pointer',
    }}
    >
      <div
        style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          padding: '4px 10px 4px 6px',
          borderRadius: 20,
          border: `${active ? 2 : 1}px ${isOverflow ? 'dashed' : 'solid'} ${color}`,
          background: theme.palette.background.paper,
          boxShadow: selected ? `0 0 0 3px ${alpha(theme.palette.primary.main, 0.3)}` : 'none',
        }}
      >
        <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
        <FindingIcon findingType={data.typeFindings ?? ''} tooltip />
        <Typography variant="body2" fontWeight={700}>
          {isOverflow ? `+${data.count ?? 0}` : (data.count ?? 0)}
        </Typography>
        <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
      </div>
      {!isOverflow && (
        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1 }}>
          {data.typeFindings}
        </Typography>
      )}
    </div>
  );
};

export default memo(FindingClusterNode);
