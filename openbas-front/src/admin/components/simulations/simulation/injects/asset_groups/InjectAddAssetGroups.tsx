import { ControlPointOutlined } from '@mui/icons-material';
import { FormHelperText, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import AssetGroupDialogAdding from '../../../../assets/asset_groups/AssetGroupDialogAdding';

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
  assetGroupIds: string[];
  onSubmit: (assetGroupIds: string[]) => void;
  disabled?: boolean;
  errorLabel?: string | null;
  label?: string | boolean;
}

const InjectAddAssetGroups: FunctionComponent<Props> = ({
  assetGroupIds,
  onSubmit,
  disabled = false,
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
          primary={t('Modify asset groups')}
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
      <AssetGroupDialogAdding
        initialState={assetGroupIds}
        open={openDialog}
        onClose={handleClose}
        onSubmit={onSubmit}
      />
    </>
  );
};

export default InjectAddAssetGroups;
