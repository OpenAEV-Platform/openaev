import { Add } from '@mui/icons-material';
import { Drawer, Fab } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchInjectorContract } from '../../../../../actions/InjectorContracts';
import { getInjectorContractSelector } from '../../../../../actions/selectors';
import { useSelectorHelper } from '../../../../../store.js';
import { type Exercise } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks.js';
import { PermissionsContext } from '../../../common/Context';
import QuickInject, { EMAIL_CONTRACT } from './QuickInject';

const useStyles = makeStyles()(theme => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 230,
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

interface Props { exercise: Exercise }

const CreateQuickInject: FunctionComponent<Props> = ({ exercise }) => {
  const dispatch = useAppDispatch();
  const { classes } = useStyles();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  const [open, setOpen] = useState(false);
  const injectorContract = useSelectorHelper(state => getInjectorContractSelector(EMAIL_CONTRACT, state));
  useEffect(() => {
    dispatch(fetchInjectorContract(EMAIL_CONTRACT));
  }, []);

  return (
    <>
      <Fab
        onClick={() => setOpen(true)}
        color="primary"
        aria-label="Add"
        className={classes.createButton}
        disabled={exercise.exercise_status !== 'RUNNING'}
      >
        <Add />
      </Fab>
      {injectorContract
        && (
          <Drawer
            open={open}
            keepMounted={false}
            anchor="right"
            sx={{ zIndex: 1202 }}
            onClose={() => setOpen(false)}
            elevation={1}
            disableEnforceFocus={true}
          >
            <QuickInject
              exerciseId={exercise.exercise_id}
              exercise={exercise}
              injectorContract={injectorContract}
              handleClose={() => setOpen(false)}
              theme={theme}
              isDisabled={permissions.readOnly}
            />
          </Drawer>
        )}
    </>
  );
};

export default CreateQuickInject;
