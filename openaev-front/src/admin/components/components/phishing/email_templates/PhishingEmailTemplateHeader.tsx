import { MailOutlineOutlined } from '@mui/icons-material';
import { Chip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type PhishingEmailTemplatesHelper } from '../../../../../actions/phishing/phishing-helper';
import { DetailHero } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type PhishingEmailTemplate } from '../../../../../utils/api-types';
import PhishingEmailTemplatePopover from './PhishingEmailTemplatePopover';

const PhishingEmailTemplateHeader = () => {
  const theme = useTheme();
  const { emailTemplateId } = useParams() as { emailTemplateId: PhishingEmailTemplate['phishing_email_template_id'] };
  const { t } = useFormatter();
  const { emailTemplate } = useHelper(
    (helper: PhishingEmailTemplatesHelper) => ({ emailTemplate: helper.getPhishingEmailTemplate(emailTemplateId) as PhishingEmailTemplate }),
  );

  const trackingOn = emailTemplate.phishing_email_template_add_tracking_pixel === true;
  const hasSenderOverride = !!(
    emailTemplate.phishing_email_template_from_name
    || emailTemplate.phishing_email_template_from_email
  );
  const chipSx = (on: boolean) => ({
    height: 22,
    fontSize: 11,
    borderRadius: 1,
    color: on ? theme.palette.success.main : theme.palette.text.secondary,
    borderColor: alpha(on ? theme.palette.success.main : theme.palette.text.secondary, 0.45),
    backgroundColor: alpha(on ? theme.palette.success.main : theme.palette.text.secondary, 0.08),
  });

  return (
    <DetailHero
      iconNode={<MailOutlineOutlined />}
      overline={t('Phishing email template')}
      title={emailTemplate.phishing_email_template_name ?? '-'}
      chips={(
        <>
          <Chip
            size="small"
            variant="outlined"
            label={trackingOn ? t('Tracking pixel') : t('No tracking pixel')}
            sx={chipSx(trackingOn)}
          />
          <Chip
            size="small"
            variant="outlined"
            label={hasSenderOverride ? t('Custom sender') : t('Platform default sender')}
            sx={chipSx(hasSenderOverride)}
          />
        </>
      )}
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
