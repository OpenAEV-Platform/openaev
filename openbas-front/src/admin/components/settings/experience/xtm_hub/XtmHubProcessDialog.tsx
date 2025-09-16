import { Close } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, IconButton } from '@mui/material';
import type React from 'react';
import { type FunctionComponent } from 'react';

interface ProcessDialogProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}

const XtmHubProcessDialog: FunctionComponent<ProcessDialogProps> = ({
  open,
  title,
  onClose,
  children,
}) => {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      slotProps={{ paper: { elevation: 1 } }}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle sx={{
        m: 0,
        p: 2,
      }}
      >
        {title}
        <IconButton
          aria-label="close"
          onClick={onClose}
          sx={{
            position: 'absolute',
            right: 8,
            top: 8,
            color: theme => theme.palette.grey[500],
          }}
        >
          <Close />
        </IconButton>
      </DialogTitle>

      <DialogContent
        dividers
        sx={{
          p: 0,
          position: 'relative',
          padding: theme => `0 ${theme.spacing(2)} ${theme.spacing(2)} ${theme.spacing(2)}`,
          border: 'none',
        }}
      >
        {children}
      </DialogContent>
    </Dialog>
  );
};

export default XtmHubProcessDialog;
