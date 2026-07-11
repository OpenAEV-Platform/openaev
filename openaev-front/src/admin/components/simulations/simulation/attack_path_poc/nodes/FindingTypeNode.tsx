import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import FindingIcon from '../../../../../../components/FindingIcon';
import { type AttackPathFlowNode } from '../attack-path-poc-flow-helpers';
import { AP_NODE_WIDTH } from './InjectorNode';

// A finding-type node (credentials, cve, port, ...) for one endpoint, iconified via FindingIcon.
const FindingTypeNode = ({ data }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  return (
    <div
      style={{
        width: AP_NODE_WIDTH * 0.75,
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        borderRadius: theme.spacing(1),
        border: `1px dashed ${theme.palette.divider}`,
        padding: theme.spacing(0.75, 1.5),
        background: theme.palette.background.paper,
      }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <FindingIcon findingType={data.typeFindings ?? ''} tooltip />
      <Typography
        variant="body2"
        sx={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {data.label}
      </Typography>
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(FindingTypeNode);
