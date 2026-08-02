import { Paper, Typography } from '@mui/material';
import { useParams } from 'react-router';

import { type PhishingEmailTemplatesHelper } from '../../../../../actions/phishing/phishing-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type PhishingEmailTemplate as PhishingEmailTemplateType } from '../../../../../utils/api-types';

const PhishingEmailTemplate = () => {
  const { emailTemplateId } = useParams() as { emailTemplateId: PhishingEmailTemplateType['phishing_email_template_id'] };
  const { t } = useFormatter();
  const { emailTemplate } = useHelper(
    (helper: PhishingEmailTemplatesHelper) => ({ emailTemplate: helper.getPhishingEmailTemplate(emailTemplateId) as PhishingEmailTemplateType }),
  );

  const previewDocument = `<!doctype html><html><head></head><body>${emailTemplate.phishing_email_template_html_body ?? ''}</body></html>`;

  return (
    <div style={{
      display: 'flex',
      gap: 24,
      alignItems: 'flex-start',
    }}
    >
      <div style={{ flex: 1 }}>
        <Typography variant="h4" gutterBottom>{t('Information')}</Typography>
        <Paper variant="outlined" sx={{ padding: 2 }}>
          <Typography variant="h3" gutterBottom>{t('Subject')}</Typography>
          {emailTemplate.phishing_email_template_subject || '-'}
          <Typography variant="h3" gutterBottom sx={{ marginTop: 2 }}>{t('Sender name override')}</Typography>
          {emailTemplate.phishing_email_template_from_name || '-'}
          <Typography variant="h3" gutterBottom sx={{ marginTop: 2 }}>{t('Sender email override')}</Typography>
          {emailTemplate.phishing_email_template_from_email || '-'}
          <Typography variant="h3" gutterBottom sx={{ marginTop: 2 }}>{t('Add tracking pixel')}</Typography>
          {emailTemplate.phishing_email_template_add_tracking_pixel ? t('Yes') : t('No')}
          <Typography
            variant="body2"
            sx={{
              marginTop: 2,
              color: 'text.secondary',
            }}
          >
            {t('Use the {{phishing_url}} placeholder in the body to insert the per-recipient tracking link that leads to the landing page.')}
          </Typography>
        </Paper>
      </div>
      <div style={{ flex: 1 }}>
        <Typography variant="h4" gutterBottom>{t('Preview')}</Typography>
        <Paper variant="outlined" sx={{ padding: 0 }}>
          <iframe
            title={emailTemplate.phishing_email_template_name}
            srcDoc={previewDocument}
            sandbox=""
            style={{
              width: '100%',
              height: 480,
              border: 0,
            }}
          />
        </Paper>
      </div>
    </div>
  );
};

export default PhishingEmailTemplate;
