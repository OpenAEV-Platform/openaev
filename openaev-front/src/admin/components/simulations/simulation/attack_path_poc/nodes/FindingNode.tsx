import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import FindingIcon from '../../../../../../components/FindingIcon';
import { type AttackPathFlowNode } from '../attack-path-poc-flow-helpers';
import { AP_FINDING_SIZE } from './FindingTypeNode';

// A leaf finding node: a circle with the type icon and the discovered value under it (a credential,
// a CVE id, a port, ...). Mirrors the product mockup's finding node.
const FindingNode = ({ data }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  return (
    <div style={{
      width: 120,
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
        <FindingIcon findingType={data.typeFindings ?? ''} />
        <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
      </div>
      <Typography
        variant="caption"
        sx={{
          maxWidth: 120,
          textAlign: 'center',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          fontSize: 10,
        }}
      >
        {data.label}
      </Typography>
    </div>
  );
};

export default memo(FindingNode);
