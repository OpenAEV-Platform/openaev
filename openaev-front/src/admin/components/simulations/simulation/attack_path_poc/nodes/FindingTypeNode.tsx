import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import FindingIcon from '../../../../../../components/FindingIcon';
import { type AttackPathFlowNode } from '../attack-path-poc-flow-helpers';

export const AP_FINDING_SIZE = 56;

// A finding-type node (credentials, cve, port, ...) for one endpoint: a circle with the type icon and
// the type name under it. Mirrors the product mockup's finding-type node.
const FindingTypeNode = ({ data }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  return (
    <div style={{
      width: AP_FINDING_SIZE,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 2,
    }}
    >
      <div
        style={{
          width: AP_FINDING_SIZE,
          height: AP_FINDING_SIZE,
          borderRadius: '50%',
          border: `1px solid ${theme.palette.divider}`,
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
      <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>
        {data.label}
      </Typography>
    </div>
  );
};

export default memo(FindingTypeNode);
