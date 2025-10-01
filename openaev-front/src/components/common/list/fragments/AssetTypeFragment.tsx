import { Chip, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { makeStyles } from 'tss-react/mui';

type Props = { type?: string };

const AssetTypeFragment = (props: Props) => {
  const theme = useTheme();
  const useStyles = makeStyles()(() => ({
    typeChip: {
      height: 20,
      borderRadius: 4,
      textTransform: 'uppercase',
      width: 100,
      marginBottom: theme.spacing(0),
    },
  }));

  const { classes } = useStyles();
  return (
    <Tooltip title={props.type}>
      <Chip
        variant="outlined"
        className={classes.typeChip}
        label={props.type}
      />
    </Tooltip>
  );
};

export default AssetTypeFragment;
