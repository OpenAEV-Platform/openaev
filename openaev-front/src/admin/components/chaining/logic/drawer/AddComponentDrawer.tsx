import { BoltOutlined, TerminalOutlined } from '@mui/icons-material';
import { ButtonBase, Divider, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { makeStyles } from 'tss-react/mui';

import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';

type ComponentType = 'action' | 'event';

interface AddComponentDrawerProps {
  open: boolean;
  onClose: () => void;
  onSelect: (type: ComponentType) => void;
}

const useStyles = makeStyles()(theme => ({
  buttons: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1),
  },
  choiceArea: {
    'display': 'grid',
    'gridTemplateColumns': '50px 1fr',
    'gridTemplateRows': 'auto auto',
    'alignItems': 'center',
    'columnGap': theme.spacing(2),
    'padding': theme.spacing(2),
    'width': '100%',
    'borderRadius': 4,
    'textAlign': 'left' as const,
    '&:hover': { backgroundColor: theme.palette.action.hover },
  },
  squareIcon: {
    display: 'grid',
    placeItems: 'center',
    gridRow: '1 / 3',
    backgroundColor: `${theme.palette.primary.main}33`,
    height: 50,
    width: 50,
    borderRadius: 4,
  },
  circleIcon: {
    display: 'grid',
    placeItems: 'center',
    gridRow: '1 / 3',
    backgroundColor: `${theme.palette.warning.main}33`,
    height: 50,
    width: 50,
    borderRadius: '50%',
  },
}));

const AddComponentDrawer = ({ open, onClose, onSelect }: AddComponentDrawerProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Add component')}
    >
      <div className={classes.buttons}>
        <ButtonBase
          className={classes.choiceArea}
          onClick={() => onSelect('action')}
        >
          <div className={classes.squareIcon}>
            <TerminalOutlined sx={{
              color: theme.palette.primary.main,
              fontSize: 24,
            }}
            />
          </div>

          <Typography variant="subtitle1" fontWeight={600}>
            {t('Action')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('Execute an injector contract with configured parameters')}
          </Typography>
        </ButtonBase>
        <Divider />
        <ButtonBase
          className={classes.choiceArea}
          onClick={() => onSelect('event')}
        >
          <div className={classes.circleIcon}>
            <BoltOutlined sx={{
              color: theme.palette.warning.main,
              fontSize: 24,
            }}
            />
          </div>
          <Typography variant="subtitle1" fontWeight={600}>
            {t('Event')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('Define conditions to trigger the next actions')}
          </Typography>
        </ButtonBase>
      </div>
    </Drawer>
  );
};

export default AddComponentDrawer;
