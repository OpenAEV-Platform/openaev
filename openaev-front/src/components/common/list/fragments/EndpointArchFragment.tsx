import { Chip } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

type Props = { arch?: string };

const useStyles = makeStyles()(theme => ({
  chip: {
    fontSize: 12,
    height: 20,
    borderRadius: 4,
    textTransform: 'uppercase',
    // Architecture carries no severity, so it uses a neutral tile rather than a colored one.
    backgroundColor: theme.palette.action.selected,
    color: theme.palette.text.primary,
  },
}));

// Architecture is only meaningful for OS-bound assets; absent / unknown values render a neutral dash
// rather than a misleading "Unknown". Present values render as a tile (like status / criticality).
const EndpointArchFragment = ({ arch }: Props) => {
  const { classes } = useStyles();
  if (!arch || arch === 'Unknown') {
    return <span>-</span>;
  }
  return <Chip classes={{ root: classes.chip }} label={arch} />;
};

export default EndpointArchFragment;
