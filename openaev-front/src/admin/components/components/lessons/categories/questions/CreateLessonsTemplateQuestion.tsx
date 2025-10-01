import { type FunctionComponent, useState } from 'react';

import { addLessonsTemplateQuestion } from '../../../../../../actions/Lessons';
import Drawer from '../../../../../../components/common/Drawer';
import ListItemButtonCreate from '../../../../../../components/common/ListItemButtonCreate';
import { useFormatter } from '../../../../../../components/i18n';
import { type LessonsTemplateCategory } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import LessonsTemplateQuestionForm, { type LessonsTemplateQuestionInputForm } from './LessonsTemplateQuestionForm';

interface Props {
  lessonsTemplateId: string;
  lessonsTemplateCategoryId: string;
}

const CreateLessonsTemplateQuestion: FunctionComponent<Props> = ({
  lessonsTemplateId,
  lessonsTemplateCategoryId,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [open, setOpen] = useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);
  const onSubmit = (data: LessonsTemplateQuestionInputForm) => {
    return dispatch(
      addLessonsTemplateQuestion(
        lessonsTemplateId,
        lessonsTemplateCategoryId,
        data,
      ),
    ).then((result: {
      result: string;
      entities: { lessonstemplatequestions: Record<string, LessonsTemplateCategory> };
    }) => {
      if (result.result) {
        return handleClose();
      }
      return result;
    });
  };
  return (
    <>
      <ListItemButtonCreate
        title={t('Create a new lessons learned question')}
        onClick={handleOpen}
      />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new lessons learned question')}
      >
        <LessonsTemplateQuestionForm onSubmit={onSubmit} handleClose={handleClose} />
      </Drawer>
    </>
  );
};

export default CreateLessonsTemplateQuestion;
