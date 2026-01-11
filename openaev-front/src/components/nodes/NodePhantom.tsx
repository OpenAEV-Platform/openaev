import { AddCircleOutline } from '@mui/icons-material';
import { type FunctionComponent, memo } from 'react';
import { makeStyles } from 'tss-react/mui';

const useStyles = makeStyles()(theme => ({
  node: {
    'border': `2px dashed ${theme.palette.divider}`,
    'borderLeft': `2px solid ${theme.palette.mode === 'dark' ? 'rgba(255, 255, 255, 0.3)' : 'rgba(0, 0, 0, 0.3)'}`,
    'borderRadius': 4,
    'width': 50,
    'height': 50,
    'padding': '8px 5px 5px 5px',
    'display': 'flex',
    'alignItems': 'center',
    'flexWrap': 'wrap',
    'textAlign': 'center',
    'backgroundColor': theme.palette.background.paper,
    'color': theme.palette.text.primary,
    'cursor': 'none !important',
    '&:hover': { backgroundColor: theme.palette.action.hover },
  },
  iconContainer: { width: '100%' },
  icon: { textAlign: 'center' },
  time: {
    position: 'relative',
    left: 60,
    top: -34,
  },
}));

interface Props {
  time: string;
  newNodeSize: number;
}

/**
 * The 'button' to create a new node when clicking on the timeline.
 * This is a fake button, as no actions are made from here, we just display a div that moves with the mouse.
 * The new node actions is triggered when clicking on the timeline (but not on a node or controls)
 * @param props the props
 * @constructor
 */
const NodePhantomComponent: FunctionComponent<Props> = (props) => {
  const { classes } = useStyles();

  return (
    <>
      <div style={{
        width: '500px',
        height: '50px',
      }}
      >
        <div
          className={classes.node}
          style={{
            height: props.newNodeSize,
            width: props.newNodeSize,
          }}
        >
          <div className={classes.iconContainer}>
            <AddCircleOutline className={classes.icon} style={{ fontSize: '30px' }} />
          </div>
        </div>
        <span className={classes.time}>
          {props.time}
        </span>
      </div>
    </>
  );
};

export default memo(NodePhantomComponent);
