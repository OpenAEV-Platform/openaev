import { type FunctionComponent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  deletePhishingEmailTemplate,
  duplicatePhishingEmailTemplate,
} from '../../../../../actions/phishing/phishing-action';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../../components/i18n';
import { type PhishingEmailTemplate } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';

interface Props {
  emailTemplate: PhishingEmailTemplate;
  inList?: boolean;
  openEditOnInit?: boolean;
  onDelete?: (result: string) => void;
}

const PhishingEmailTemplatePopover: FunctionComponent<Props> = ({
  emailTemplate,
  inList = false,
  openEditOnInit = false,
  onDelete,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const ability = useAbility();

  const [openDelete, setOpenDelete] = useState(false);

  const editPath = `/admin/components/phishing/email_templates/${emailTemplate.phishing_email_template_id}/edit`;

  // Legacy deep link (?id=...) used to auto-open the edit drawer; now it lands
  // directly on the full-page editor.
  useEffect(() => {
    if (openEditOnInit) {
      navigate(editPath);
    }
  }, [openEditOnInit]);

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

  const entries = [{
    label: 'Update',
    action: () => {
      navigate(editPath);
    },
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PHISHING),
  }, {
    label: 'Duplicate',
    action: () => {
      submitDuplicate();
    },
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PHISHING),
  }, {
    label: 'Delete',
    action: () => setOpenDelete(true),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.PHISHING),
  }];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
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
