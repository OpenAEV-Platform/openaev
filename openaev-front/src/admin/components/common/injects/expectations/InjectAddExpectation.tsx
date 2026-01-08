import { ControlPointOutlined } from '@mui/icons-material';
import { ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import Dialog from '../../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../../components/i18n';
import { type ExpectationInput, type ExpectationInputForm } from './Expectation';
import ExpectationFormCreate from './ExpectationFormCreate';

const useStyles = makeStyles()(theme => ({
  text: {
    fontSize: theme.typography.h2.fontSize,
    color: theme.palette.primary.main,
    fontWeight: theme.typography.h2.fontWeight,
  },
}));

interface InjectAddExpectationProps {
  predefinedExpectations: ExpectationInput[];
  handleAddExpectation: (data: ExpectationInput) => void;
}

const InjectAddExpectation: FunctionComponent<InjectAddExpectationProps> = ({
  predefinedExpectations,
  handleAddExpectation,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  // Dialog
  const [openDialog, setOpenDialog] = useState(false);
  const handleOpen = () => setOpenDialog(true);
  const handleClose = () => setOpenDialog(false);

  // Form
  const onSubmit = (data: ExpectationInputForm) => {
    const values: ExpectationInput = {
      ...data,
      expectation_expiration_time: data.expiration_time_days * 3600 * 24
        + data.expiration_time_hours * 3600
        + data.expiration_time_minutes * 60,
    };
    handleAddExpectation(values);
    handleClose();
  };

  return (
    <>
      <ListItemButton
        divider={true}
        onClick={handleOpen}
        color="primary"
      >
        <ListItemIcon color="primary">
          <ControlPointOutlined color="primary" />
        </ListItemIcon>
        <ListItemText
          primary={t('Add expectations')}
          classes={{ primary: classes.text }}
        />
      </ListItemButton>
      <Dialog
        open={openDialog}
        handleClose={handleClose}
        title={t('Add expectation in this inject')}
      >
        <ExpectationFormCreate
          predefinedExpectations={predefinedExpectations}
          onSubmit={onSubmit}
          handleClose={handleClose}
        />
      </Dialog>
    </>
  );
};

export default InjectAddExpectation;
