import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import FindingIcon from '../../../../../../components/FindingIcon';
import attackPathStatusColor from '../attack-path-colors';
import { type AttackPathFlowNode, maskFindingValue } from '../attack-path-flow-helpers';
import { AP_FINDING_SIZE } from './node-sizes';

// A leaf finding node: the type icon with the discovered value to its right (only the value, no type
// name). The target handle sits on the icon so the incoming edge reaches it with no gap.
const FindingNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  // Verdict colour (green/orange/red) by default; blue only when this finding is the selected path.
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const color = selected ? theme.palette.primary.main : verdict;
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
    }}
    >
      <div
        style={{
          position: 'relative',
          flex: '0 0 auto',
          width: AP_FINDING_SIZE,
          height: AP_FINDING_SIZE,
          borderRadius: '50%',
          border: `${selected ? 2 : 1}px solid ${color}`,
          background: theme.palette.background.paper,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
        <FindingIcon findingType={data.typeFindings ?? ''} />
        <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
      </div>
      <Typography
        variant="caption"
        sx={{ whiteSpace: 'nowrap' }}
      >
        {maskFindingValue(data.typeFindings, data.label)}
      </Typography>
    </div>
  );
};

export default memo(FindingNode);
