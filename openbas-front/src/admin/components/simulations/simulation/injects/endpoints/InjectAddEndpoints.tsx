import { ControlPointOutlined } from '@mui/icons-material';
import { FormHelperText, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import EndpointsDialogAdding from '../../../../assets/endpoints/EndpointsDialogAdding';

const useStyles = makeStyles()(theme => ({
  text: {
    fontSize: theme.typography.h2.fontSize,
    color: theme.palette.primary.main,
    fontWeight: theme.typography.h2.fontWeight,
  },
  textError: {
    fontSize: theme.typography.h2.fontSize,
    color: theme.palette.error.main,
    fontWeight: theme.typography.h2.fontWeight,
  },
}));

interface Props {
  disabled?: boolean;
  endpointIds: string[];
  onSubmit: (endpointIds: string[]) => void;
  platforms?: string[];
  payloadArch?: string;
  errorLabel?: string | null;
  label?: string | boolean;
}

const InjectAddEndpoints: FunctionComponent<Props> = ({
  disabled = false,
  endpointIds,
  onSubmit,
  platforms,
  payloadArch,
  errorLabel = null,
  label,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  // Dialog
  const [openDialog, setOpenDialog] = useState(false);
  const handleOpen = () => setOpenDialog(true);
  const handleClose = () => setOpenDialog(false);

  return (
    <>
      <ListItemButton
        divider={true}
        onClick={handleOpen}
        disabled={disabled}
      >
        <ListItemIcon>
          <ControlPointOutlined color={errorLabel ? 'error' : 'primary'} />
        </ListItemIcon>
        <ListItemText
          primary={t('Modify assets')}
          classes={{ primary: errorLabel ? classes.textError : classes.text }}
        />
      </ListItemButton>
      {!errorLabel && label && (
        <FormHelperText>
          {label}
        </FormHelperText>
      )}
      {errorLabel && (
        <FormHelperText error>
          {errorLabel}
        </FormHelperText>
      )}
      <EndpointsDialogAdding
        initialState={endpointIds}
        open={openDialog}
        platforms={platforms}
        payloadArch={payloadArch}
        onClose={handleClose}
        onSubmit={onSubmit}
        title={t('Modify assets in this inject')}
      />
    </>
  );
};

export default InjectAddEndpoints;
