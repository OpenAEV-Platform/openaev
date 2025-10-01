import { type FunctionComponent, useState } from 'react';

import { addLessonsTemplate } from '../../../../actions/Lessons';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Dialog from '../../../../components/common/dialog/Dialog';
import Drawer from '../../../../components/common/Drawer';
import ListItemButtonCreate from '../../../../components/common/ListItemButtonCreate';
import { useFormatter } from '../../../../components/i18n';
import { type LessonsTemplate, type LessonsTemplateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import LessonsTemplateForm from './LessonsTemplateForm';

interface Props {
  inline?: boolean;
  onCreate?: (result: LessonsTemplate) => void;
}

const CreateLessonsTemplate: FunctionComponent<Props> = ({
  inline,
  onCreate,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [open, setOpen] = useState(false);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);
  const onSubmit = (data: LessonsTemplateInput) => {
    return dispatch(addLessonsTemplate(data)).then((result: {
      result: string;
      entities: { lessonstemplates: Record<string, LessonsTemplate> };
    }) => {
      if (result.result) {
        if (onCreate) {
          const created = result.entities.lessonstemplates[result.result];
          onCreate(created);
        }
        return handleClose();
      }
      return result;
    });
  };
  return (
    <>
      {inline ? (
        <ListItemButtonCreate
          title={t('Create a new lessons learned template')}
          onClick={handleOpen}
        />
      ) : (
        <ButtonCreate onClick={handleOpen} />
      )}
      {inline ? (
        <Dialog
          open={open}
          handleClose={handleClose}
          title={t('Create a new lessons learned template')}
        >
          <LessonsTemplateForm onSubmit={onSubmit} handleClose={handleClose} />
        </Dialog>
      ) : (
        <Drawer
          open={open}
          handleClose={handleClose}
          title={t('Create a new lessons learned template')}
        >
          <LessonsTemplateForm onSubmit={onSubmit} handleClose={handleClose} />
        </Drawer>
      )}
    </>
  );
};

export default CreateLessonsTemplate;
