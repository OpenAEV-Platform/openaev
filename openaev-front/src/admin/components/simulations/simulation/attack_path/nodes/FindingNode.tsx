import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import FindingIcon from '../../../../../../components/FindingIcon';
import attackPathStatusColor from '../attack-path-colors';
import { type AttackPathFlowNode, maskFindingValue } from '../attack-path-flow-helpers';
import { AP_FINDING_SIZE } from './node-sizes';

// A leaf finding node: the type icon with the discovered value ABOVE it (only the value, no type name),
// kept off the horizontal path so the incoming/outgoing edges never overlap the label. The handles sit on
// the icon so the edges reach it with no gap.
const FindingNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  // Verdict colour (green/orange/red) by default; blue only when this finding is the selected path.
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const color = selected ? theme.palette.primary.main : verdict;
  return (
    <div
      style={{
        position: 'relative',
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
      {/* Value centred above the icon, in the verdict (expectation-result) colour, so the causal edge
          leaving the icon on the right never crushes it. */}
      <Typography
        variant="caption"
        sx={{
          position: 'absolute',
          bottom: '100%',
          left: '50%',
          transform: 'translateX(-50%)',
          mb: 0.5,
          whiteSpace: 'nowrap',
          fontWeight: 700,
          color,
        }}
      >
        {maskFindingValue(data.typeFindings, data.label)}
      </Typography>
      <FindingIcon findingType={data.typeFindings ?? ''} />
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(FindingNode);
