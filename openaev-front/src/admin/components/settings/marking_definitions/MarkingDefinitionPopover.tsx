import { type FunctionComponent, useContext, useState } from 'react';

import {
  deleteMarkingDefinition,
  updateMarkingDefinition,
} from '../../../../actions/marking_definitions/marking-definition-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import {
  type MarkingDefinitionInput,
  type MarkingDefinitionOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import MarkingDefinitionForm from './MarkingDefinitionForm';
import {
  extractMarkingDefinitionFromStoreResult,
  type MarkingDefinitionStoreResult,
} from './MarkingDefinitionStoreHelper';

interface Props {
  markingDefinition: MarkingDefinitionOutput;
  onDelete?: (id: string) => void;
  onUpdate?: (result: MarkingDefinitionOutput) => void;
}

const MarkingDefinitionPopover: FunctionComponent<Props> = ({
  markingDefinition,
  onDelete,
  onUpdate,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.MARKING_DEFINITION);
  const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.MARKING_DEFINITION);

  const [openUpdate, setOpenUpdate] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const isProtected = markingDefinition.marking_definition_protected;

  const updateInputFromDefinition
    = (value: MarkingDefinitionOutput): MarkingDefinitionInput => ({
      marking_definition_type: value.marking_definition_type,
      marking_definition_definition: value.marking_definition_definition,
      marking_definition_color: value.marking_definition_color,
      marking_definition_order: value.marking_definition_order,
    });

  const submitUpdate = (input: MarkingDefinitionInput) => {
    if (input.marking_definition_order !== markingDefinition.marking_definition_order) {
      const confirmed = window.confirm(t('Changing order can impact precedence. Do you want to continue?'));
      if (!confirmed) {
        return Promise.resolve();
      }
    }
    return dispatch(updateMarkingDefinition(markingDefinition.marking_definition_id, input))
      .then((result: MarkingDefinitionStoreResult) => {
        const updatedMarkingDefinition = extractMarkingDefinitionFromStoreResult(result);
        if (updatedMarkingDefinition) {
          onUpdate?.(updatedMarkingDefinition);
          setOpenUpdate(false);
        }
        return result;
      })
      .catch((error: unknown) => error);
  };

  const submitDelete = () => {
    return dispatch(deleteMarkingDefinition(markingDefinition.marking_definition_id))
      .then(() => {
        onDelete?.(markingDefinition.marking_definition_id);
        setOpenDelete(false);
      })
      .catch((error: unknown) => error);
  };

  const entries: PopoverEntry[] = [
    {
      label: 'Update',
      action: () => setOpenUpdate(true),
      userRight: canManage && !isProtected,
    },
    {
      label: 'Delete',
      action: () => setOpenDelete(true),
      userRight: canDelete && !isProtected,
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer open={openUpdate} handleClose={() => setOpenUpdate(false)} title={t('Update a marking definition')}>
        <MarkingDefinitionForm
          isEdit
          defaultValues={updateInputFromDefinition(markingDefinition)}
          onSubmit={submitUpdate}
        />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this marking definition?')}
      />
    </>
  );
};

export default MarkingDefinitionPopover;
