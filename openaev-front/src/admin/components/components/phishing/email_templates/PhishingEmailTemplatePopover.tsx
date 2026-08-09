import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  deletePhishingEmailTemplate,
  duplicatePhishingEmailTemplate,
  updatePhishingEmailTemplate,
} from '../../../../../actions/phishing/phishing-action';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { type PhishingEmailTemplate } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import PhishingEmailTemplateForm, { type PhishingEmailTemplateFormInput } from './PhishingEmailTemplateForm';

interface Props {
  emailTemplate: PhishingEmailTemplate;
  inList?: boolean;
  openEditOnInit?: boolean;
  onUpdate?: (result: PhishingEmailTemplate) => void;
  onDelete?: (result: string) => void;
}

const PhishingEmailTemplatePopover: FunctionComponent<Props> = ({
  emailTemplate,
  inList = false,
  openEditOnInit = false,
  onUpdate,
  onDelete,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);

  const [openEdit, setOpenEdit] = useState(openEditOnInit);
  const [openDelete, setOpenDelete] = useState(false);

  const onSubmitEdit = async (data: PhishingEmailTemplateFormInput) => {
    const result = await dispatch(updatePhishingEmailTemplate(emailTemplate.phishing_email_template_id, data));
    if (onUpdate && result.entities) {
      onUpdate(result.entities.phishingemailtemplates[result.result]);
    }
    setOpenEdit(false);
  };

  const submitDelete = async () => {
    await dispatch(deletePhishingEmailTemplate(emailTemplate.phishing_email_template_id));
    setOpenDelete(false);
    if (onDelete) {
      onDelete(emailTemplate.phishing_email_template_id);
    }
    if (!inList) {
      navigate('/admin/components/phishing/email_templates');
    }
  };

  const submitDuplicate = async () => {
    await dispatch(duplicatePhishingEmailTemplate(emailTemplate.phishing_email_template_id));
  };

  const initialValues: PhishingEmailTemplateFormInput = {
    phishing_email_template_name: emailTemplate.phishing_email_template_name ?? '',
    phishing_email_template_description: emailTemplate.phishing_email_template_description ?? '',
    phishing_email_template_subject: emailTemplate.phishing_email_template_subject ?? '',
    phishing_email_template_html_body: emailTemplate.phishing_email_template_html_body ?? '',
    phishing_email_template_text_body: emailTemplate.phishing_email_template_text_body ?? '',
    phishing_email_template_from_name: emailTemplate.phishing_email_template_from_name ?? '',
    phishing_email_template_from_email: emailTemplate.phishing_email_template_from_email ?? '',
    phishing_email_template_add_tracking_pixel: emailTemplate.phishing_email_template_add_tracking_pixel ?? true,
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
        title={t('Update the phishing email template')}
      >
        <PhishingEmailTemplateForm
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
        text={t('Do you want to delete this phishing email template?')}
      />
    </>
  );
};

export default PhishingEmailTemplatePopover;
