import { useState } from 'react';

import { addPhishingEmailTemplate } from '../../../../../actions/phishing/phishing-action';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { useAppDispatch } from '../../../../../utils/hooks';
import PhishingEmailTemplateForm, { type PhishingEmailTemplateFormInput } from './PhishingEmailTemplateForm';

const CreatePhishingEmailTemplate = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  const onSubmit = async (data: PhishingEmailTemplateFormInput) => {
    const result = await dispatch(addPhishingEmailTemplate(data));
    if (result.result) {
      setOpen(false);
    }
    return result;
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new phishing email template')}
      >
        <PhishingEmailTemplateForm onSubmit={onSubmit} handleClose={() => setOpen(false)} />
      </Drawer>
    </>
  );
};

export default CreatePhishingEmailTemplate;
