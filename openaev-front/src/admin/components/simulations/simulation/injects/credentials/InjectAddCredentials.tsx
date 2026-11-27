import { ControlPointOutlined } from '@mui/icons-material';
import { FormHelperText, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import { type CredentialOutput } from '../../../../../../utils/api-types';
import CredentialsPicker from '../../../../assets/credentials/CredentialsPicker';

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
  const { t } = useFormatter();

  // Dialog
  const [openDialog, setOpenDialog] = useState(false);
  const handleOpen = () => setOpenDialog(true);
  const handleClose = () => setOpenDialog(false);

  const color = errorLabel ? 'error' : 'primary';

  return (
    <>
      <ListItemButton
        divider
        onClick={handleOpen}
        disabled={disabled}
      >
        <ListItemIcon sx={{ minWidth: theme => theme.spacing(3.75) }}>
          <ControlPointOutlined color={color} fontSize="small" />
        </ListItemIcon>
        <ListItemText
          primary={multiple ? t('Update credentials') : t('Update credential')}
          slotProps={{
            primary: {
              sx: {
                color: `${color}.main`,
                fontWeight: 'fontWeightMedium',
              },
            },
          }}
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
