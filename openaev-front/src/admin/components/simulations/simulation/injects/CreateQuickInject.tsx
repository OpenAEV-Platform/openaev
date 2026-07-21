import { Drawer } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';

import { type InjectorContractHelper } from '../../../../../actions/injector_contracts/injector-contract-helper';
import { fetchInjectorContract } from '../../../../../actions/InjectorContracts';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import { useHelper } from '../../../../../store';
import { type Exercise, type InjectorContract } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { PermissionsContext } from '../../../common/Context';
import QuickInject, { EMAIL_CONTRACT } from './QuickInject';

interface Props { exercise: Exercise }

const CreateQuickInject: FunctionComponent<Props> = ({ exercise }) => {
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const { permissions } = useContext(PermissionsContext);

  const [open, setOpen] = useState(false);
  const { injectorContract }: { injectorContract: InjectorContract }
    = useHelper((helper: InjectorContractHelper) => ({ injectorContract: helper.getInjectorContract(EMAIL_CONTRACT) }));
  useEffect(() => {
    dispatch(fetchInjectorContract(EMAIL_CONTRACT));
  }, []);

  return (
    <>
      <ButtonCreate
        onClick={() => setOpen(true)}
        disabled={exercise.exercise_status !== 'RUNNING'}
      />
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
