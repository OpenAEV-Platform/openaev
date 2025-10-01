import { FlagOutlined, HelpOutlined, ModeStandbyOutlined, ScoreOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';
import { makeStyles } from 'tss-react/mui';

// Deprecated - https://mui.com/system/styles/basics/
// Do not use it for new code.
const useStyles = makeStyles()(theme => ({
  node: {
    position: 'relative',
    border:
      theme.palette.mode === 'dark'
        ? '1px solid rgba(255, 255, 255, 0.12)'
        : '1px solid rgba(0, 0, 0, 0.12)',
    borderRadius: 4,
    width: 200,
    height: 110,
    padding: '8px 5px 5px 5px',
  },
  icon: {
    textAlign: 'center',
    margin: '10px 0 10px 0',
  },
  label: {
    margin: '0 auto',
    textAlign: 'center',
    fontSize: 15,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  description: {
    maxWidth: 100,
    color:
      theme.palette.mode === 'dark'
        ? 'rgba(255, 255, 255, 0.5)'
        : 'rgba(0, 0, 0, 0.5)',
    fontSize: 8,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
}));

const renderIcon = (icon: string) => {
  switch (icon) {
    case 'attack-started':
      return <FlagOutlined />;
    case 'attack-ended':
      return <ModeStandbyOutlined />;
    case 'result':
      return <ScoreOutlined />;
    default:
      return <HelpOutlined />;
  }
};

export type NodeResultStep = Node<{
  background?: string;
  color?: string;
  key: string;
  label: string | React.JSX.Element;
  description?: string;
  end: boolean;
  middle: boolean;
  start: boolean;
}

>;

const NodeResultStepComponent = ({ data }: NodeProps<NodeResultStep>) => {
  const { classes } = useStyles();
  return (
    <div
      className={classes.node}
      style={{
        backgroundColor: data.background,
        color: data.color,
      }}
    >
      <div className={classes.icon}>
        {renderIcon(data.key)}
      </div>
      <Tooltip title={data.label}>
        <div className={classes.label}>{data.label}</div>
      </Tooltip>
      <Tooltip title={data.description}>
        <div className={classes.description}>{data.description}</div>
      </Tooltip>
      {(data.end || data.middle) && (
        <Handle
          type="target"
          position={Position.Left}
          isConnectable={false}
        />
      )}
      {(data.start || data.middle) && (
        <Handle
          type="source"
          position={Position.Right}
          isConnectable={false}
        />
      )}
    </div>
  );
};

export default memo(NodeResultStepComponent);
