import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type Node, type NodeProps } from '@xyflow/react';
import { memo } from 'react';

export type TacticGroupNodeData = Node<{ label: string }>;

const TacticGroupNode = ({ data }: NodeProps<TacticGroupNodeData>) => {
  const theme = useTheme();

  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <Typography
        variant="caption"
        sx={{
          color: theme.palette.text.secondary,
          textTransform: 'capitalize',
          display: 'block',
          textAlign: 'center',
          mb: 1,
        }}
      >
        {data.label}
      </Typography>
      <div
        style={{
          background: `${theme.palette.primary.main}18`,
          borderRadius: 8,
          flex: 1,
        }}
      />
    </div>
  );
};

export default memo(TacticGroupNode);
