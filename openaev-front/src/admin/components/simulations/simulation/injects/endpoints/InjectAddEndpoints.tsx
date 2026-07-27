import { ControlPointOutlined } from '@mui/icons-material';
import { FormHelperText, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import EndpointsPicker from '../../../../assets/endpoints/EndpointsPicker';

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
        <ListItemIcon classes={{ root: classes.icon }}>
          <ControlPointOutlined color={errorLabel ? 'error' : 'primary'} fontSize="small" />
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
      <EndpointsPicker
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
