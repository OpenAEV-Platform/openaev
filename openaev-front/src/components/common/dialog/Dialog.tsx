import { Close } from '@mui/icons-material';
import { Box, type Breakpoint, Dialog as MuiDialog, DialogActions, DialogContent, DialogTitle, IconButton } from '@mui/material';
import { type FunctionComponent, type ReactElement, type ReactNode } from 'react';

import Transition from '../Transition';

type DialogSize = 'small' | 'medium' | 'large';

const DIALOG_SIZES: Record<DialogSize, string> = {
  small: '420px',
  medium: '640px',
  large: '960px',
};

interface DialogProps {
  open: boolean;
  handleClose: () => void;
  children: (() => ReactElement) | ReactElement | null;
  title?: ReactNode;
  maxWidth?: Breakpoint;
  size?: DialogSize;
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
  size,
  actions,
  className,
  showCloseIcon = false,
}) => {
  const renderContent = typeof children === 'function' ? children() : children;

  const sizeStyle = size
    ? {
        '& .MuiDialog-paper': {
          maxWidth: DIALOG_SIZES[size],
          width: '100%',
        },
      } as const
    : undefined;

  return (
    <MuiDialog
      className={className}
      open={open}
      onClose={handleClose}
      fullWidth={!size}
      maxWidth={size ? false : maxWidth}
      slots={{ transition: Transition }}
      slotProps={{
        paper: {
          elevation: 1,
          sx: size ? { paddingTop: 3 } : undefined,
        },
      }}
      sx={sizeStyle}
    >
      {title && (
        <DialogTitle sx={size ? {
          paddingY: 0,
          paddingX: 3,
          mb: 2,
          display: 'flex',
          alignItems: 'center',
          justifyContent: showCloseIcon && !title ? 'flex-end' : 'space-between',
        } : undefined}
        >
          {showCloseIcon ? (
            <Box display="flex" alignItems="center" justifyContent="space-between" width="100%">
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
