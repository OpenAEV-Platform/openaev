import { Close } from '@mui/icons-material';
import { Chip, Drawer as DrawerMUI, IconButton, type PaperProps, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { cloneElement, type CSSProperties, type FunctionComponent, type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

import { computeBannerSettings } from '../../public/components/systembanners/utils';
import { getSeverityAndColor } from '../../utils/Colors';
import useAuth from '../../utils/hooks/useAuth';

const useStyles = makeStyles()(theme => ({
  drawerPaperHalf: {
    minHeight: '100vh',
    width: '50%',
    position: 'fixed',
    overflow: 'auto',
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
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.enteringScreen,
    }),
    backgroundColor: theme.palette.background.default,
  },
  header: {
    backgroundColor: theme.palette.mode === 'light' ? theme.palette.background.default : theme.palette.background.nav,
    padding: '10px 0',
    display: 'flex',
    alignItems: 'center',
  },
  headerFull: {
    backgroundColor: theme.palette.mode === 'light' ? theme.palette.background.default : theme.palette.background.nav,
    borderBottom: `1px solid ${theme.palette.divider}`,
    display: 'inline-flex',
    alignItems: 'center',
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
  const theme = useTheme();
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
      elevation={variant === 'full' ? 0 : 1}
      sx={{ zIndex: 1202 }}
      classes={{ paper: variant === 'full' ? classes.drawerPaperFull : classes.drawerPaperHalf }}
      onClose={handleClose}
      PaperProps={PaperProps}
      ModalProps={{ disableEnforceFocus }}
    >
      <div className={variant === 'full' ? classes.headerFull : classes.header} style={{ marginTop: bannerHeightNumber }}>
        <IconButton
          aria-label="Close"
          onClick={handleClose}
          size="large"
          color="primary"
        >
          <Close fontSize="small" color="primary" />
        </IconButton>
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          flex: 1,
          minWidth: 0,
        }}
        >
          <Tooltip title={title}>
            <Typography
              variant="subtitle2"
              noWrap
            >
              {title}
            </Typography>
          </Tooltip>
          {(additionalTitle || additionalChipLabel) && (
            <div style={{
              display: 'flex',
              float: 'right',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: 10,
              paddingRight: theme.spacing(2),
            }}
            >
              {additionalTitle && (<Typography variant="subtitle1">{additionalTitle}</Typography>)}
              {additionalChipLabel && (
                <Chip
                  label={additionalChipLabel}
                  size="small"
                  variant="outlined"
                  sx={{
                    borderColor: color,
                    color: color,
                  }}
                />
              )}
            </div>
          )}
        </div>
      </div>
      <div style={{
        padding: '10px 20px 20px 20px',
        ...containerStyle,
      }}
      >
        {component}
      </div>
    </DrawerMUI>
  );
};

export default Drawer;
