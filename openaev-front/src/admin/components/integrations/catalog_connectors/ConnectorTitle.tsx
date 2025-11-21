import { Chip, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

const useStyles = makeStyles()(theme => ({
  content: {
    display: 'flex',
    gap: theme.spacing(2),
  },
  img: {
    width: 60,
    height: 60,
    borderRadius: 4,
  },
  titleContainer: {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'top',
  },

  chipInList: {
    fontSize: 12,
    height: 20,
    flexShrink: 0,
    alignSelf: 'flex-start',
    textTransform: 'uppercase',
    borderRadius: 4,
  },
}));

type ConnectorHeaderProps = {
  connectorId: string;
  connectorLogo?: string;
  connectorTitle: string;
  connectorType?: string;
};

const ConnectorTitle = ({ connectorId, connectorLogo, connectorTitle, connectorType }: ConnectorHeaderProps) => {
  // Standard hooks
  const { classes } = useStyles();

  return (
    <div className={classes.content}>
      <img
        src={`/api/images/catalog/connectors/logos/${connectorLogo}`}
        alt={connectorId}
        className={classes.img}
      />
      <div className={classes.titleContainer}>
        <Typography
          variant="h1"
          style={{
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {connectorTitle}
        </Typography>
        <Chip
          variant="outlined"
          classes={{ root: classes.chipInList }}
          color="primary"
          label={connectorType}
        />
      </div>
    </div>
  );
};

export default ConnectorTitle;
