import { Close } from '@mui/icons-material';
import { Chip, Drawer as DrawerMUI, type PaperProps, Stack, Tooltip, Typography } from '@mui/material';
import { cloneElement, type CSSProperties, type FunctionComponent, type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

import { computeBannerSettings } from '../../public/components/systembanners/utils';
import { getSeverityAndColor } from '../../utils/Colors';
import useAuth from '../../utils/hooks/useAuth';
import IconButton from './button/IconButton';

const useStyles = makeStyles()(theme => ({
  drawerPaperHalf: {
    minHeight: '100vh',
    width: '50%',
    position: 'fixed',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.enteringScreen,
    }),
  },
  drawerPaperFull: {
    minHeight: '100vh',
    width: '100vw',
    position: 'fixed',
    overflow: 'auto',
    backgroundColor: theme.palette.background.default,
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.enteringScreen,
    }),
  },
  header: {
    backgroundColor: theme.palette.background.secondary,
    paddingLeft: theme.spacing(3),
    paddingRight: theme.spacing(3),
    paddingTop: theme.spacing(2),
    paddingBottom: theme.spacing(2),
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  headerFull: {
    backgroundColor: theme.palette.background.secondary,
    borderBottom: `1px solid ${theme.palette.divider}`,
    paddingLeft: theme.spacing(3),
    paddingRight: theme.spacing(3),
    paddingTop: theme.spacing(2),
    paddingBottom: theme.spacing(2),
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  container: {
    padding: theme.spacing(3),
    flex: 1,
    overflowY: 'auto',
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2),
    backgroundColor: theme.palette.mode === 'dark' ? '#0f1d34' : '#FFFFFF',
  },
}));

interface DrawerProps {
  open: boolean;
  handleClose: () => void;
  title: string;
  additionalTitle?: string;
  additionalChipLabel?: string;
  children:
    (() => ReactElement)
    | ReactElement
    | null;
  variant?: 'full' | 'half';
  PaperProps?: PaperProps;
  disableEnforceFocus?: boolean;
  containerStyle?: CSSProperties;
}

const Drawer: FunctionComponent<DrawerProps> = ({
  open = false,
  handleClose,
  title,
  additionalTitle,
  additionalChipLabel,
  children,
  variant = 'half',
  PaperProps = undefined,
  disableEnforceFocus = false,
  containerStyle = {},
}) => {
  const { settings } = useAuth();
  const { bannerHeightNumber } = computeBannerSettings(settings);

  const { classes } = useStyles();
  let component;
  if (children) {
    if (typeof children === 'function') {
      component = children();
    } else {
      component = cloneElement(children as ReactElement);
    }
  }

  const { color } = getSeverityAndColor(additionalChipLabel);

  return (
    <DrawerMUI
      open={open}
      anchor="right"
      elevation={1}
      sx={{ zIndex: 1202 }}
      classes={{ paper: variant === 'full' ? classes.drawerPaperFull : classes.drawerPaperHalf }}
      onClose={handleClose}
      PaperProps={PaperProps}
      ModalProps={{ disableEnforceFocus }}
    >
      <div
        className={variant === 'full' ? classes.headerFull : classes.header}
        style={{ marginTop: bannerHeightNumber }}
      >
        <Stack direction="row" alignItems="center" gap={1} sx={{ minWidth: 0 }}>
          <Tooltip title={title}>
            <Typography
              variant="h5"
              noWrap
              style={{ textWrap: 'nowrap' }}
            >
              {title}
            </Typography>
          </Tooltip>
          {(additionalTitle || additionalChipLabel) && (
            <Stack direction="row" alignItems="center" gap={1}>
              {additionalTitle && (<Typography variant="subtitle1">{additionalTitle}</Typography>)}
              {additionalChipLabel && (
                <Chip
                  label={additionalChipLabel}
                  size="small"
                  variant="outlined"
                  sx={{
                    borderColor: color,
                    color,
                  }}
                />
              )}
            </Stack>
          )}
        </Stack>
        <IconButton
          aria-label="Close"
          onClick={handleClose}
          size="default"
        >
          <Close />
        </IconButton>
      </div>
      <div
        className={classes.container}
        style={containerStyle}
      >
        {component}
      </div>
    </DrawerMUI>
  );
};

export default Drawer;
