import { Add } from '@mui/icons-material';
import { Button, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';

export type LogicContext = 'scenario' | 'simulation';

interface AddComponentButtonProps {
  nodeCount: number;
  context: LogicContext;
  onClick?: () => void;
}

const useStyles = makeStyles()(theme => ({
  button: {
    position: 'absolute',
    top: 10,
    right: 10,
    zIndex: 5,
  },
  buttonEmptyCanvas: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: theme.spacing(2),
  },
}));

const AddComponentButton = ({
  nodeCount,
  context,
  onClick,
}: AddComponentButtonProps) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  const isEmptyCanvas = nodeCount === 0;

  const button = (
    <Button
      variant="contained"
      color="primary"
      size={isEmptyCanvas ? 'large' : 'medium'}
      startIcon={<Add />}
      onClick={onClick}
    >
      {t('Add component')}
    </Button>
  );

  if (!isEmptyCanvas) {
    return (
      <div className={classes.button}>
        {button}
      </div>
    );
  }

  return (
    <div className={classes.buttonEmptyCanvas}>
      <Typography variant="body1" color="text.secondary" align="center">
        {t(
          `Start adding components to complete the configuration of your ${context}.`,
        )}
      </Typography>
      {button}
    </div>
  );
};

export default AddComponentButton;
