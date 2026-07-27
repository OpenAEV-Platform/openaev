import { ControlPointOutlined } from '@mui/icons-material';
import {
  Button,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import Dialog from '../../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../../components/i18n';
import { type ExpectationInput, type ExpectationInputForm } from './Expectation';
import ExpectationFormCreate from './ExpectationFormCreate';

const useStyles = makeStyles()(theme => ({
  icon: { minWidth: 30 },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

interface InjectAddExpectationProps {
  availableExpectations: ExpectationInput[];
  handleAddExpectation: (data: ExpectationInput) => void;
  disabled?: boolean;
  inline?: boolean;
}

const InjectAddExpectation: FunctionComponent<InjectAddExpectationProps> = ({
  availableExpectations,
  handleAddExpectation,
  disabled,
  inline = false,
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
      {inline
        ? (
            <Button
              size="small"
              variant="text"
              color="primary"
              startIcon={<ControlPointOutlined />}
              onClick={handleOpen}
              disabled={disabled}
            >
              {t('Add expectations')}
            </Button>
          )
        : (
            <ListItemButton
              divider={true}
              onClick={handleOpen}
              color="primary"
              disabled={disabled}
            >
              <ListItemIcon color="primary" classes={{ root: classes.icon }}>
                <ControlPointOutlined color="primary" fontSize="small" />
              </ListItemIcon>
              <ListItemText
                primary={t('Add expectations')}
                classes={{ primary: classes.text }}
              />
            </ListItemButton>
          )}
      <Dialog
        open={openDialog}
        handleClose={handleClose}
        title={t('Add expectation in this inject')}
      >
        <ExpectationFormCreate
          availableExpectations={availableExpectations}
          onSubmit={onSubmit}
          handleClose={handleClose}
        />
      </Dialog>
    </>
  );
};

export default InjectAddExpectation;
