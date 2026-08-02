import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  deletePhishingLandingPage,
  duplicatePhishingLandingPage,
  updatePhishingLandingPage,
} from '../../../../../actions/phishing/phishing-action';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type PhishingLandingPage } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import PhishingLandingPageForm, { type PhishingLandingPageFormInput } from './PhishingLandingPageForm';

interface Props {
  landingPage: PhishingLandingPage;
  inList?: boolean;
}

const PhishingLandingPagePopover: FunctionComponent<Props> = ({ landingPage, inList = false }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);

  const [openEdit, setOpenEdit] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const onSubmitEdit = async (data: PhishingLandingPageFormInput) => {
    await dispatch(updatePhishingLandingPage(landingPage.phishing_landing_page_id, data));
    setOpenEdit(false);
  };

  const submitDelete = async () => {
    await dispatch(deletePhishingLandingPage(landingPage.phishing_landing_page_id));
    setOpenDelete(false);
    if (!inList) {
      navigate('/admin/components/phishing/landing_pages');
    }
  };

  const submitDuplicate = async () => {
    await dispatch(duplicatePhishingLandingPage(landingPage.phishing_landing_page_id));
  };

  const initialValues: PhishingLandingPageFormInput = {
    phishing_landing_page_name: landingPage.phishing_landing_page_name ?? '',
    phishing_landing_page_description: landingPage.phishing_landing_page_description ?? '',
    phishing_landing_page_html: landingPage.phishing_landing_page_html ?? '',
    phishing_landing_page_css: landingPage.phishing_landing_page_css ?? '',
    phishing_landing_page_capture_submitted_data: landingPage.phishing_landing_page_capture_submitted_data ?? true,
    phishing_landing_page_capture_passwords: landingPage.phishing_landing_page_capture_passwords ?? true,
    phishing_landing_page_redirect_url: landingPage.phishing_landing_page_redirect_url ?? '',
    phishing_landing_page_primary_color_dark: landingPage.phishing_landing_page_primary_color_dark ?? '',
    phishing_landing_page_primary_color_light: landingPage.phishing_landing_page_primary_color_light ?? '',
  };

  const entries = [{
    label: 'Update',
    action: () => setOpenEdit(true),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PHISHING),
  }, {
    label: 'Duplicate',
    action: () => submitDuplicate(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PHISHING),
  }, {
    label: 'Delete',
    action: () => setOpenDelete(true),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.PHISHING),
  }];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer
        open={openEdit}
        handleClose={() => setOpenEdit(false)}
        title={t('Update the phishing landing page')}
      >
        <PhishingLandingPageForm
          initialValues={initialValues}
          editing
          onSubmit={onSubmitEdit}
          handleClose={() => setOpenEdit(false)}
        />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this phishing landing page?')}
      />
    </>
  );
};

export default PhishingLandingPagePopover;
