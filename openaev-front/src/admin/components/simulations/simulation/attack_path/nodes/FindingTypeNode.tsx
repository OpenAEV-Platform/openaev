import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import FindingIcon from '../../../../../../components/FindingIcon';
import attackPathStatusColor from '../attack-path-colors';
import { type AttackPathFlowNode } from '../attack-path-flow-helpers';
import { AP_FINDING_SIZE } from './node-sizes';

// A finding-type node (credentials, cve, port, ...) for one endpoint: an icon-only circle. The type is
// named once, on the edge into it (and on hover via the icon tooltip), so it is not repeated here.
const FindingTypeNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  // Verdict colour (green/orange/red) by default; blue only when selected.
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const color = selected ? theme.palette.primary.main : verdict;
  return (
    <div
      style={{
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
      <FindingIcon findingType={data.typeFindings ?? ''} tooltip />
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(FindingTypeNode);
