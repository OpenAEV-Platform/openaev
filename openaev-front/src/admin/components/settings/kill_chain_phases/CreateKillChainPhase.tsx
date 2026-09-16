import { type FunctionComponent, useState } from 'react';

import { addKillChainPhase } from '../../../../actions/KillChainPhase';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type KillChainPhase, type KillChainPhaseCreateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import KillChainPhaseForm from './KillChainPhaseForm';

interface Props { onCreate?: (result: KillChainPhase) => void }

const CreateKillChainPhase: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  const handleClose = () => setOpen(false);

  const onSubmit = (data: KillChainPhaseCreateInput) => {
    return dispatch(addKillChainPhase(data)).then(
      (result: {
        result: string;
        entities: { killchainphases: Record<string, KillChainPhase> };
      }) => {
        if (result.result) {
          onCreate?.(result.entities.killchainphases[result.result]);
          handleClose();
        }
        return result;
      },
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new kill chain phase')}
      >
        <KillChainPhaseForm
          onSubmit={onSubmit}
          handleClose={handleClose}
        />
      </Drawer>
    </>
  );
};

export default CreateKillChainPhase;
