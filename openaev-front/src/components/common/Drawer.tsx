import { Chip, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Close } from '@mui/icons-material';
import { Drawer as DrawerMUI, type PaperProps, Typography } from '@mui/material';
import { cloneElement, type CSSProperties, type FunctionComponent, type ReactElement, type ReactNode } from 'react';
import { makeStyles } from 'tss-react/mui';

import { computeBannerSettings } from '../../public/components/systembanners/utils';
import { CVSS_SEVERITY, getSeverityAndColor } from '../../utils/Colors';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../utils/fdsLayer';
import useAuth from '../../utils/hooks/useAuth';

// Same surfaces as the sibling product's Drawer: the paper is a layer-2 surface
// (`layer-2` + the three input aliases, see utils/fdsLayer.ts), the header sits on
// the heading token and the body on the default token of that layer.
const useStyles = makeStyles()(theme => ({
  drawerPaperHalf: {
    minHeight: '100vh',
    width: '50%',
    position: 'fixed',
    overflow: 'auto',
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
    display: 'flex',
    flexDirection: 'column',
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.enteringScreen,
    }),
  },
  // Title on the left, actions + close on the right, over the heading band.
  header: {
    backgroundColor: 'var(--bg-elevation-heading)',
    padding: theme.spacing(2, 3),
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: theme.spacing(1),
    flexShrink: 0,
  },
  headerFull: {
    backgroundColor: 'var(--bg-elevation-heading)',
    padding: theme.spacing(2, 3),
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: theme.spacing(1),
    flexShrink: 0,
  },
  // The scrollable body content.
  // `flex: 1` makes it fill the paper height BELOW the header without adding the
  // header's height on top of a `100%` min-height (which produced a phantom
  // scrollbar that scrolled by exactly the header height).
  container: {
    backgroundColor: 'var(--bg-elevation-default)',
    flex: 1,
    padding: '10px 20px 20px 20px',
  },
}));

interface DrawerProps {
  open: boolean;
  handleClose: () => void;
  title: string;
  additionalTitle?: string;
  additionalChipLabel?: string;
  /** Custom content rendered in the header band, right-aligned before the close button. */
  headerActions?: ReactNode;
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
  headerActions,
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

  // The chip takes the tone, not a colour: its `color` prop is hex-only.
  const { severity } = getSeverityAndColor(additionalChipLabel);

  return (
    <DrawerMUI
      open={open}
      anchor="right"
      elevation={variant === 'full' ? 0 : 1}
      sx={{
        // Offset the whole drawer below the top system banner (EE trial, etc.)
        // so the header sits flush at the drawer top instead of leaving an empty
        // gap above it.
        'zIndex': 1202,
        '& .MuiDrawer-paper': {
          ...layerInputVars,
          top: bannerHeightNumber,
          minHeight: `calc(100vh - ${bannerHeightNumber}px)`,
        },
      }}
      classes={{ paper: `fds-drawer-surface ${fdsLayerClass(SURFACE_LAYER)} ${variant === 'full' ? classes.drawerPaperFull : classes.drawerPaperHalf}` }}
      onClose={handleClose}
      PaperProps={PaperProps}
      ModalProps={{ disableEnforceFocus }}
      // Many call sites mount the drawer lazily, already open ({condition && <Drawer open ...>}).
      // MUI skips the enter transition on first render (appear is tied to an internal mounted
      // ref), which made those drawers pop in without the slide effect. Forcing `appear`
      // guarantees the design-system slide-from-right animation everywhere.
      slotProps={{ transition: { appear: true } }}
    >
      <div className={variant === 'full' ? classes.headerFull : classes.header}>
        <Tooltip>
          <TooltipTrigger asChild>
            <Typography
              variant="h5"
              noWrap
              sx={{
                flex: 1,
                minWidth: 0,
                margin: 0,
              }}
            >
              {title}
            </Typography>
          </TooltipTrigger>
          {title && <TooltipContent>{title}</TooltipContent>}
        </Tooltip>
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
          gap: 10,
        }}
        >
          {headerActions}
          {additionalTitle && (<Typography variant="subtitle1">{additionalTitle}</Typography>)}
          {additionalChipLabel && (
            <Chip label={additionalChipLabel} severity={CVSS_SEVERITY[severity]} />
          )}
          <IconButton icon={<Close />} aria-label="Close" onClick={handleClose} priority="tertiary" size="md" />
        </div>
      </div>
      <div className={classes.container} style={containerStyle}>
        {component}
      </div>
    </DrawerMUI>
  );
};

export default Drawer;
