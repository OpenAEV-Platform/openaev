import { type FunctionComponent, useContext, useState } from 'react';

import { deleteLessonsTemplateCategory, updateLessonsTemplateCategory } from '../../../../../actions/Lessons';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type LessonsTemplateCategory } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import LessonsTemplateCategoryForm, { type LessonsTemplateCategoryInputForm } from './LessonsTemplateCategoryForm';

interface Props {
  lessonsTemplateId: string;
  lessonsTemplateCategory: LessonsTemplateCategory;
}

const LessonsTemplateCategoryPopover: FunctionComponent<Props> = ({
  lessonsTemplateId,
  lessonsTemplateCategory,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const initialValues = {
    lessons_template_category_name: lessonsTemplateCategory.lessons_template_category_name,
    lessons_template_category_description: lessonsTemplateCategory.lessons_template_category_description,
    lessons_template_category_order: lessonsTemplateCategory.lessons_template_category_order?.toString(),
  };

  // Edition
  const [openEdit, setOpenEdit] = useState(false);
  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => setOpenEdit(false);
  const onSubmitEdit = (data: LessonsTemplateCategoryInputForm) => {
    return dispatch(
      updateLessonsTemplateCategory(
        lessonsTemplateId,
        lessonsTemplateCategory.lessonstemplatecategory_id,
        data,
      ),
    ).then(() => handleCloseEdit());
  };

  // Deletion
  const [openDelete, setOpenDelete] = useState(false);
  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);
  const submitDelete = () => {
    dispatch(
      deleteLessonsTemplateCategory(
        lessonsTemplateId,
        lessonsTemplateCategory.lessonstemplatecategory_id,
      ),
    ).then(() => handleCloseDelete());
  };

  const entries = [
    {
      label: 'Update',
      action: handleOpenEdit,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.LESSONS_LEARNED),
    },
    {
      label: 'Delete',
      action: handleOpenDelete,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.LESSONS_LEARNED), // Manage and not delete because deleting a template is updating the lessons.
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the lessons learned category')}
      >
        <LessonsTemplateCategoryForm
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
          initialValues={initialValues}
          editing
        />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this lessons learned category?')}
      />
    </>
  );
};

export default LessonsTemplateCategoryPopover;
