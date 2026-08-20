import { type FunctionComponent, useState } from 'react';

import { addLessonsTemplateCategory } from '../../../../../actions/Lessons';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type LessonsTemplateCategory } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import LessonsTemplateCategoryForm, { type LessonsTemplateCategoryInputForm } from './LessonsTemplateCategoryForm';

interface Props {
  lessonsTemplateId: string;
  /** Optional explicit button label (defaults to the generic "Create"). */
  label?: string;
}

const CreateLessonsTemplateCategory: FunctionComponent<Props> = ({ lessonsTemplateId, label }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [open, setOpen] = useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);
  const onSubmit = (data: LessonsTemplateCategoryInputForm) => {
    return dispatch(addLessonsTemplateCategory(lessonsTemplateId, data)).then(
      (result: {
        result: string;
        entities: { lessonstemplatecategorys: Record<string, LessonsTemplateCategory> };
      }) => {
        if (result.result) {
          return handleClose();
        }
        return result;
      },
    );
  };
  return (
    <>
      <ButtonCreate onClick={handleOpen} label={label} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new lessons learned category')}
      >
        <LessonsTemplateCategoryForm onSubmit={onSubmit} handleClose={handleClose} />
      </Drawer>
    </>
  );
};

export default CreateLessonsTemplateCategory;
