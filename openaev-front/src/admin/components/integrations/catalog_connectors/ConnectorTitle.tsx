import { Chip, Tooltip, Typography } from '@mui/material';
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
    justifyContent: 'flex-start',
    width: '100%',
  },
  title: {
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    maxWidth: '85%',
  },
  titleNoEllipsis: {
    whiteSpace: 'normal',
    overflow: 'visible',
    textOverflow: 'unset',
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
  noEllipsis?: boolean;
};

const ConnectorTitle = ({
  connectorId,
  connectorLogo,
  connectorTitle,
  connectorType,
  noEllipsis = false,
}: ConnectorHeaderProps) => {
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
        <Tooltip title={connectorTitle}>
          <Typography
            variant="h1"
            className={noEllipsis ? classes.titleNoEllipsis : classes.title}
          >
            {connectorTitle}
          </Typography>
        </Tooltip>
        <Chip
          variant="outlined"
          className={classes.chipInList}
          color="primary"
          label={connectorType}
        />
      </div>
    </div>
  );
};

export default ConnectorTitle;
