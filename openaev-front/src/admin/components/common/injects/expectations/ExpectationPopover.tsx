import { type FunctionComponent, useContext, useState } from 'react';

import { type LoggedHelper } from '../../../../../actions/helper';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import Dialog from '../../../../../components/common/dialog/Dialog';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type InjectExpectation, type PlatformSettings } from '../../../../../utils/api-types';
import { AbilityContext } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../../utils/permissions/types';
import { PermissionsContext } from '../../Context';
import { type ExpectationInput, type ExpectationInputForm } from './Expectation';
import ExpectationFormUpdate from './ExpectationFormUpdate';
import useExpectationExpirationTime from './useExpectationExpirationTime';

interface ExpectationPopoverProps {
  index: number;
  expectation: ExpectationInput;
  injectId?: string;
  handleUpdate: (data: ExpectationInput, idx: number) => void;
  handleDelete: (idx: number) => void;
}

const ExpectationPopover: FunctionComponent<ExpectationPopoverProps> = ({
  index,
  expectation,
  injectId,
  handleUpdate,
  handleDelete,
}) => {
  // Standard hooks
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const { t } = useFormatter();
  const { permissions, inherited_context } = useContext(PermissionsContext);
  const ability = useContext(AbilityContext);
  const userManageExpectations = permissions.canManage || ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT)
    || (inherited_context === INHERITED_CONTEXT.NONE && ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId));

  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);

  // Hook must be called at the top level of the component
  const defaultExpirationTime = useExpectationExpirationTime(expectation.expectation_type as InjectExpectation['inject_expectation_type']);

  const getExpirationTime = (expirationTime: number): number => {
    if (expirationTime !== null && expirationTime !== undefined) {
      return expirationTime;
    }
    return defaultExpirationTime;
  };

  const initialValues = {
    expectation_type: expectation.expectation_type ?? '',
    expectation_name: expectation.expectation_name ?? '',
    expectation_description: expectation.expectation_description ?? '',
    expectation_score: expectation.expectation_score ?? settings.expectation_manual_default_score_value,
    expectation_expectation_group: expectation.expectation_expectation_group ?? false,
    expectation_expiration_time: getExpirationTime(expectation.expectation_expiration_time),
  };

  // Edition
  const handleOpenEdit = () => {
    setOpenEdit(true);
  };
  const handleCloseEdit = () => setOpenEdit(false);

  const onSubmitEdit = (data: ExpectationInputForm) => {
    const values: ExpectationInput = {
      ...data,
      expectation_expiration_time: data.expiration_time_days * 3600 * 24
        + data.expiration_time_hours * 3600
        + data.expiration_time_minutes * 60,
    };
    handleUpdate(values, index);
    handleCloseEdit();
  };

  // Deletion
  const handleOpenDelete = () => {
    setOpenDelete(true);
  };
  const handleCloseDelete = () => setOpenDelete(false);

  const onSubmitDelete = () => {
    handleDelete(index);
    handleCloseDelete();
  };

  // Button Popover
  const entries = [
    {
      label: 'Update',
      action: () => handleOpenEdit(),
      userRight: userManageExpectations,
    }, {
      label: 'Remove',
      action: () => handleOpenDelete(),
      userRight: userManageExpectations,
    }];

  return (
    <div>
      <ButtonPopover entries={entries} variant="icon" />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={onSubmitDelete}
        text={t('Do you want to delete this expectation?')}
      />
      <Dialog
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the expectation')}
      >
        <ExpectationFormUpdate
          initialValues={initialValues}
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
        />
      </Dialog>
    </div>
  );
};

export default ExpectationPopover;
