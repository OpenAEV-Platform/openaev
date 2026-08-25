import { type FunctionComponent, useContext, useState } from 'react';

import { deleteMarkingDefinition, updateMarkingDefinition } from '../../../../actions/markings/marking-definition-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type MarkingDefinitionInput, type MarkingDefinitionOutput } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import MarkingDefinitionForm from './MarkingDefinitionForm';

interface Props {
  marking: MarkingDefinitionOutput;
  onUpdate?: (marking: MarkingDefinitionOutput) => void;
  onDelete?: (markingId: string) => void;
}

const MarkingDefinitionPopover: FunctionComponent<Props> = ({
  marking,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  const [openEdit, setOpenEdit] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const onSubmitEdit = (data: MarkingDefinitionInput) => {
    return updateMarkingDefinition(marking.marking_id, data).then((result: { data: MarkingDefinitionOutput }) => {
      onUpdate?.(result.data);
      setOpenEdit(false);
      return result;
    });
  };

  const submitDelete = () => {
    deleteMarkingDefinition(marking.marking_id).then(() => onDelete?.(marking.marking_id));
    setOpenDelete(false);
  };

  const entries = [
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

  const initialValues: MarkingDefinitionInput = {
    marking_type: marking.marking_type,
    marking_name: marking.marking_name,
    marking_order: marking.marking_order,
    marking_color: marking.marking_color,
  };

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this marking definition?')}
      />
      <Drawer
        open={openEdit}
        handleClose={() => setOpenEdit(false)}
        title={t('Update the marking definition')}
      >
        <MarkingDefinitionForm
          initialValues={initialValues}
          editing
          onSubmit={onSubmitEdit}
          onCancel={() => setOpenEdit(false)}
        />
      </Drawer>
    </>
  );
};

export default MarkingDefinitionPopover;
