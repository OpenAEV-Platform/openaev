import { Close } from '@mui/icons-material';
import { Box, type Breakpoint, Dialog as MuiDialog, DialogActions, DialogContent, DialogTitle, IconButton } from '@mui/material';
import { type FunctionComponent, type ReactElement, type ReactNode } from 'react';

import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../utils/fdsLayer';
import Transition from '../Transition';

interface DialogProps {
  open: boolean;
  handleClose: () => void;
  children: (() => ReactElement) | ReactElement | null;
  title?: ReactNode;
  maxWidth?: Breakpoint;
  className?: string;
  actions?: ReactElement | null;
  showCloseIcon?: boolean;
}

const Dialog: FunctionComponent<DialogProps> = ({
  open = false,
  handleClose,
  children,
  title,
  maxWidth = 'md',
  actions,
  className,
  showCloseIcon = false,
}) => {
  const renderContent = typeof children === 'function' ? children() : children;

  return (
    <MuiDialog
      className={className}
      open={open}
      onClose={handleClose}
      fullWidth
      maxWidth={maxWidth}
      slots={{ transition: Transition }}
      // A dialog is a layer-2 surface like a drawer: the layer class and the input aliases go on the paper.
      slotProps={{
        paper: {
          elevation: 1,
          className: fdsLayerClass(SURFACE_LAYER),
          sx: {
            ...layerInputVars,
            backgroundColor: 'var(--bg-elevation-default)',
            backgroundImage: 'none',
          },
        },
      }}
    >
      {title && (
        <DialogTitle>
          {showCloseIcon ? (
            <Box display="flex" alignItems="center" justifyContent="space-between">
              {title}
              <IconButton onClick={handleClose} size="small" aria-label="close">
                <Close />
              </IconButton>
            </Box>
          ) : (
            title
          )}
        </DialogTitle>
      )}
      <DialogContent>{renderContent}</DialogContent>
      {actions && <DialogActions>{actions}</DialogActions>}
    </MuiDialog>
  );
};

export default Dialog;
