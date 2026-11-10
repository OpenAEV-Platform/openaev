import { Alert, DialogContentText } from '@mui/material';
import { type FunctionComponent, useMemo, useState } from 'react';

import {
  deleteMarkingDefinition,
  fetchMarkingDefinitionAssigned,
  updateMarkingDefinition,
} from '../../../../actions/marking_definitions/marking-definition-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogConfirmation from '../../../../components/common/DialogConfirmation';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import {
  type MarkingDefinitionInput,
  type MarkingDefinitionOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
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
  const ability = useAbility();
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.MARKING_DEFINITION);
  const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.MARKING_DEFINITION);

  const [openUpdate, setOpenUpdate] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);
  const [isAssigned, setIsAssigned] = useState(false);
  const [openOrderConfirm, setOpenOrderConfirm] = useState(false);
  const [pendingUpdateInput, setPendingUpdateInput] = useState<MarkingDefinitionInput | null>(null);

  const isProtected = markingDefinition.marking_definition_protected;
  const protectedTooltip = t('This marking definition is protected. It cannot be updated or deleted.');

  // Stable reference: the form resets whenever its default values change.
  const updateDefaultValues = useMemo<MarkingDefinitionInput>(() => ({
    marking_definition_type: markingDefinition.marking_definition_type,
    marking_definition_definition: markingDefinition.marking_definition_definition,
    marking_definition_color: markingDefinition.marking_definition_color,
    marking_definition_order: markingDefinition.marking_definition_order,
  }), [markingDefinition]);

  const performUpdate = (input: MarkingDefinitionInput) => {
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

  const submitUpdate = (input: MarkingDefinitionInput) => {
    if (input.marking_definition_order !== markingDefinition.marking_definition_order) {
      setPendingUpdateInput(input);
      setOpenOrderConfirm(true);
      return Promise.resolve();
    }
    return performUpdate(input);
  };

  const confirmOrderUpdate = () => {
    if (!pendingUpdateInput) {
      setOpenOrderConfirm(false);
      return Promise.resolve();
    }
    return performUpdate(pendingUpdateInput).finally(() => {
      setOpenOrderConfirm(false);
      setPendingUpdateInput(null);
    });
  };

  // The backend still deletes an assigned definition (removing it from every entity that holds
  // it), so the check only warns the user before they confirm.
  const handleOpenDelete = () => {
    fetchMarkingDefinitionAssigned(markingDefinition.marking_definition_id)
      .then((result: { data: boolean }) => {
        setIsAssigned(result.data);
      })
      .catch(() => setIsAssigned(false))
      .finally(() => setOpenDelete(true));
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
      disabled: isProtected,
      disabledMessage: protectedTooltip,
      userRight: canManage,
    },
    {
      label: 'Delete',
      action: handleOpenDelete,
      disabled: isProtected,
      disabledMessage: protectedTooltip,
      userRight: canDelete,
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} disabledTooltip={isProtected ? protectedTooltip : undefined} />
      <Drawer open={openUpdate} handleClose={() => setOpenUpdate(false)} title={t('Update a marking definition')}>
        <MarkingDefinitionForm
          isEdit
          defaultValues={updateDefaultValues}
          onSubmit={submitUpdate}
        />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this marking definition?')}
        richContent={isAssigned ? (
          <>
            <DialogContentText>
              {t('Do you want to delete this marking definition?')}
            </DialogContentText>
            <Alert severity="warning" style={{ marginTop: 16 }}>
              {t('This marking definition is currently assigned. Deleting it will remove it from every entity that carries it.')}
            </Alert>
          </>
        ) : undefined}
      />
      <DialogConfirmation
        open={openOrderConfirm}
        handleClose={() => {
          setOpenOrderConfirm(false);
          setPendingUpdateInput(null);
        }}
        handleSubmit={confirmOrderUpdate}
        text={t('Changing order can impact precedence. Do you want to continue?')}
        submitLabel={t('Update')}
      />
    </>
  );
};

export default MarkingDefinitionPopover;
