import { ControlPointOutlined } from '@mui/icons-material';
import { FormHelperText, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import { type CredentialOutput } from '../../../../../../utils/api-types';
import CredentialsPicker from '../../../../assets/credentials/CredentialsPicker';

const useStyles = makeStyles()(theme => ({
  icon: { minWidth: 30 },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
  textError: {
    fontSize: 15,
    color: theme.palette.error.main,
    fontWeight: 500,
  },
}));

interface Props {
  disabled?: boolean;
  selectedCredentialIds: string[];
  onSubmit: (credentialIds: string[]) => void;
  errorLabel?: string | null;
  label?: string | boolean;
  multiple?: boolean;
  credentialType?: CredentialOutput['credential_type'];
}

const InjectAddCredentials: FunctionComponent<Props> = ({
  disabled = false,
  selectedCredentialIds,
  onSubmit,
  errorLabel = null,
  label,
  multiple = true,
  credentialType,
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
        <ListItemIcon classes={{ root: classes.icon }}>
          <ControlPointOutlined color={errorLabel ? 'error' : 'primary'} fontSize="small" />
        </ListItemIcon>
        <ListItemText
          primary={multiple ? t('Update credentials') : t('Update credential')}
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
      <CredentialsPicker
        initialState={selectedCredentialIds}
        open={openDialog}
        onClose={handleClose}
        onSubmit={onSubmit}
        title={multiple ? t('Update credentials in this inject') : t('Update credential in this inject')}
        multiple={multiple}
        credentialType={credentialType}
      />
    </>
  );
};

export default InjectAddCredentials;
