import { Add, ControlPointOutlined } from '@mui/icons-material';
import {
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type VariableInput } from '../../../../utils/api-types';
import { VariableContext } from '../../common/Context';
import VariableForm from './VariableForm';

const useStyles = makeStyles()(theme => ({
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

interface Props { inline?: boolean }

const CreateVariable: FunctionComponent<Props> = ({ inline }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  // Context
  const { onCreateVariable } = useContext(VariableContext);

  // Creation
  const [open, setOpen] = useState(false);
  const onSubmit = (data: VariableInput) => {
    onCreateVariable(data);
    setOpen(false);
  };

  return (
    <>
      {inline ? (
        <ListItemButton divider onClick={() => setOpen(true)} color="primary">
          <ListItemIcon color="primary">
            <ControlPointOutlined color="primary" />
          </ListItemIcon>
          <ListItemText
            primary={t('Create a new variable')}
            classes={{ primary: classes.text }}
          />
        </ListItemButton>
      ) : (
        <IconButton
          color="primary"
          aria-label="Add"
          onClick={() => setOpen(true)}
          size="small"
        >
          <Add fontSize="small" />
        </IconButton>
      )}
      <Dialog
        open={open}
        slots={{ transition: Transition }}
        onClose={() => setOpen(false)}
        fullWidth
        maxWidth="md"
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogTitle>{t('Create a new variable')}</DialogTitle>
        <DialogContent>
          <VariableForm
            onSubmit={onSubmit}
            handleClose={() => setOpen(false)}
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default CreateVariable;
