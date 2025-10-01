import { type FunctionComponent, useContext, useState } from 'react';

import { deleteLessonsTemplateQuestion, updateLessonsTemplateQuestion } from '../../../../../../actions/Lessons';
import ButtonPopover from '../../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../../components/common/DialogDelete';
import Drawer from '../../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../../components/i18n';
import { type LessonsTemplateQuestion } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import { AbilityContext } from '../../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import LessonsTemplateQuestionForm, { type LessonsTemplateQuestionInputForm } from './LessonsTemplateQuestionForm';

interface Props {
  lessonsTemplateId: string;
  lessonsTemplateCategoryId: string;
  lessonsTemplateQuestion: LessonsTemplateQuestion;
}

const LessonsTemplateQuestionPopover: FunctionComponent<Props> = ({
  lessonsTemplateId,
  lessonsTemplateCategoryId,
  lessonsTemplateQuestion,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const initialValues = {
    lessons_template_question_content: lessonsTemplateQuestion.lessons_template_question_content,
    lessons_template_question_explanation: lessonsTemplateQuestion.lessons_template_question_explanation,
    lessons_template_question_order: lessonsTemplateQuestion.lessons_template_question_order?.toString(),
  };

  // Edition
  const [openEdit, setOpenEdit] = useState(false);
  const handleOpenEdit = () => setOpenEdit(true);
  const handleCloseEdit = () => setOpenEdit(false);
  const onSubmitEdit = (data: LessonsTemplateQuestionInputForm) => {
    return dispatch(
      updateLessonsTemplateQuestion(
        lessonsTemplateId,
        lessonsTemplateCategoryId,
        lessonsTemplateQuestion.lessonstemplatequestion_id,
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
      deleteLessonsTemplateQuestion(
        lessonsTemplateId,
        lessonsTemplateCategoryId,
        lessonsTemplateQuestion.lessonstemplatequestion_id,
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
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.LESSONS_LEARNED), // Manage and not delete because deleting a template question is updating the lessons.
    },
  ];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the lessons learned question')}
      >
        <LessonsTemplateQuestionForm
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
        text={t('Do you want to delete this lessons learned question?')}
      />
    </>
  );
};

export default LessonsTemplateQuestionPopover;
