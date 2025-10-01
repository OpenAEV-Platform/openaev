import { CheckOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { Alert, Button, CircularProgress, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Link } from 'react-router';

import Dialog from '../dialog/Dialog';

interface Props {
  open: boolean;
  isSubmitting: boolean;
  successMessage: string;
  redirectButtonLabel: string;
  redirectLink: string;
  loadMessage: string;
  onClose: () => void;
}

const LoaderDialog = ({
  open,
  isSubmitting,
  loadMessage,
  successMessage,
  redirectButtonLabel,
  redirectLink,
  onClose,
}: Props) => {
  const theme = useTheme();
  return (
    <Dialog
      open={open}
      handleClose={() => onClose()}
      title=""
      maxWidth="xs"
    >
      <div style={{
        textAlign: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}
      >

        {isSubmitting && (
          <>
            <CircularProgress size={200} thickness={0.3} />

            <Typography
              variant="caption"
              component="div"
              sx={{
                color: 'text.secondary',
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
              }}
            >
              {loadMessage}
            </Typography>
          </>
        )}
        {!isSubmitting && (
          <>
            <Alert icon={<CheckOutlined fontSize="inherit" />} severity="success">
              {successMessage}
            </Alert>
            <Button
              sx={{ marginTop: theme.spacing(1) }}
              component={Link}
              to={redirectLink}
              variant="outlined"
              endIcon={<OpenInNewOutlined />}
              onClick={() => onClose()}
            >
              {redirectButtonLabel}
            </Button>
          </>
        )}
      </div>

    </Dialog>
  );
};

export default LoaderDialog;
