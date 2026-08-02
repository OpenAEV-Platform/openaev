import { MailOutlineOutlined } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useParams } from 'react-router';

import { type PhishingEmailTemplatesHelper } from '../../../../../actions/phishing/phishing-helper';
import { DetailHero } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type PhishingEmailTemplate } from '../../../../../utils/api-types';
import PhishingEmailTemplatePopover from './PhishingEmailTemplatePopover';

const PhishingEmailTemplateHeader = () => {
  const { emailTemplateId } = useParams() as { emailTemplateId: PhishingEmailTemplate['phishing_email_template_id'] };
  const { t } = useFormatter();
  const { emailTemplate } = useHelper(
    (helper: PhishingEmailTemplatesHelper) => ({ emailTemplate: helper.getPhishingEmailTemplate(emailTemplateId) as PhishingEmailTemplate }),
  );

  return (
    <DetailHero
      iconNode={<MailOutlineOutlined />}
      overline={t('Phishing email template')}
      title={emailTemplate.phishing_email_template_name ?? '-'}
      action={<PhishingEmailTemplatePopover emailTemplate={emailTemplate} />}
      footer={emailTemplate.phishing_email_template_description
        ? (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {emailTemplate.phishing_email_template_description}
            </Typography>
          )
        : undefined}
    />
  );
};

export default PhishingEmailTemplateHeader;
