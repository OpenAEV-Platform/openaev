import { useState } from 'react';

import { addPhishingLandingPage } from '../../../../../actions/phishing/phishing-action';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { useAppDispatch } from '../../../../../utils/hooks';
import PhishingLandingPageForm, { type PhishingLandingPageFormInput } from './PhishingLandingPageForm';

const CreatePhishingLandingPage = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  const onSubmit = async (data: PhishingLandingPageFormInput) => {
    const result = await dispatch(addPhishingLandingPage(data));
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
        title={t('Create a new phishing landing page')}
      >
        <PhishingLandingPageForm onSubmit={onSubmit} handleClose={() => setOpen(false)} />
      </Drawer>
    </>
  );
};

export default CreatePhishingLandingPage;
