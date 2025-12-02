import { VerifiedOutlined } from '@mui/icons-material';
import { Chip, Tooltip, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import colorStyles from '../../../../components/Color';
import { useFormatter } from '../../../../components/i18n';

const useStyles = makeStyles()(theme => ({
  content: {
    display: 'grid',
    gridTemplateColumns: '60px auto 1fr',
    gridTemplateRows: 'auto auto',
    columnGap: theme.spacing(2),
    alignItems: 'start',
  },
  img: {
    gridRow: 'span 2',
    width: 60,
    height: 60,
    borderRadius: 4,
  },
  title: {
    gridColumn: 2,
    gridRow: 1,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    maxWidth: '100%',
  },
  titleNoEllipsis: {
    gridColumn: 2,
    gridRow: 1,
    whiteSpace: 'normal',
    overflow: 'visible',
    textOverflow: 'unset',
  },
  chips: {
    gridColumn: 2,
    gridRow: 2,
  },
  chipInList: {
    margin: theme.spacing(0.25),
    fontSize: 12,
    height: 20,
    flexShrink: 0,
    justifySelf: 'start',
    textTransform: 'uppercase',
    width: 'auto',
    borderRadius: 4,
  },
  chipVerified: {
    padding: theme.spacing(2),
    fontSize: 12,
    height: 20,
    flexShrink: 0,
    justifySelf: 'start',
    textTransform: 'uppercase',
    width: 'auto',
    borderRadius: 4,
  },
  customizable: {
    position: 'absolute',
    top: 10,
    right: 10,
  },
}));

type ConnectorHeaderProps = {
  connectorId: string;
  connectorLogo?: string;
  connectorTitle: string;
  connectorType?: string;
  connectorUseCases?: string[];
  detailsTitle?: boolean;
};

const ConnectorTitle = ({
  connectorId,
  connectorLogo,
  connectorTitle,
  connectorType,
  connectorUseCases,
  detailsTitle = false,
}: ConnectorHeaderProps) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  return (
    <div className={classes.content}>
      <img
        src={`/api/images/catalog/connectors/logos/${connectorLogo}`}
        alt={connectorId}
        className={classes.img}
      />
      <Tooltip title={connectorTitle}>
        <Typography
          variant="h1"
          className={detailsTitle ? classes.titleNoEllipsis : classes.title}
        >
          {connectorTitle}
        </Typography>
      </Tooltip>
      <div className={classes.chips}>
        <Chip
          variant="outlined"
          className={classes.chipInList}
          color="primary"
          label={connectorType}
        />
        {connectorUseCases && connectorUseCases.map((useCase: string) => (
          <Chip
            key={useCase}
            variant="outlined"
            className={classes.chipInList}
            color="default"
            label={useCase}
          />
        ))}
      </div>

      {detailsTitle
        ? (
            <Chip
              variant="filled"
              className={classes.chipVerified}
              style={colorStyles.green}
              icon={<VerifiedOutlined color="success" />}
              label={t('Verified')}
            />
          ) : (
            <Tooltip title={t('Verified')} className={classes.customizable}>
              <VerifiedOutlined color="success" />
            </Tooltip>
          )}
    </div>
  );
};

export default ConnectorTitle;
