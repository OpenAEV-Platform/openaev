import { type FunctionComponent, useContext, useState } from 'react';

import { deleteKillChainPhase, updateKillChainPhase } from '../../../../actions/KillChainPhase';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type KillChainPhase, type KillChainPhaseCreateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import KillChainPhaseForm from './KillChainPhaseForm';

interface Props {
  killChainPhase: KillChainPhase;
  onUpdate?: (result: KillChainPhase) => void;
  onDelete?: (result: string) => void;
}

const KillChainPhasePopover: FunctionComponent<Props> = ({ killChainPhase, onUpdate, onDelete }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [openEdit, setOpenEdit] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const handleCloseEdit = () => setOpenEdit(false);
  const handleCloseDelete = () => setOpenDelete(false);

  const onSubmitEdit = (data: KillChainPhaseCreateInput) => {
    return dispatch(updateKillChainPhase(killChainPhase.phase_id, data)).then(
      (result: {
        result: string;
        entities: { killchainphases: Record<string, KillChainPhase> };
      }) => {
        if (result?.result) {
          onUpdate?.(result.entities.killchainphases[result.result]);
        }
        handleCloseEdit();
        return result;
      },
    );
  };

  const submitDelete = () => {
    dispatch(deleteKillChainPhase(killChainPhase.phase_id)).then(() => {
      onDelete?.(killChainPhase.phase_id);
    });
    handleCloseDelete();
  };

  const entries: PopoverEntry[] = [
    {
      label: 'Update',
      action: () => setOpenEdit(true),
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS),
    },
    {
      label: 'Delete',
      action: () => setOpenDelete(true),
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS),
    },
  ];

  const initialValues: Partial<KillChainPhaseCreateInput> = {
    phase_name: killChainPhase.phase_name,
    phase_shortname: killChainPhase.phase_shortname,
    phase_kill_chain_name: killChainPhase.phase_kill_chain_name,
    phase_order: killChainPhase.phase_order,
    phase_external_id: killChainPhase.phase_external_id,
  };

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this kill chain phase?')}
      />
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the kill chain phase')}
      >
        <KillChainPhaseForm
          initialValues={initialValues}
          editing
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
        />
      </Drawer>
    </>
  );
};

export default KillChainPhasePopover;
