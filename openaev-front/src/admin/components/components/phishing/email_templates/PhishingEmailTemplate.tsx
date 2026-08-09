import { InfoOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useParams } from 'react-router';

import { type PhishingEmailTemplatesHelper } from '../../../../../actions/phishing/phishing-helper';
import { DetailSections, Field, InformationGrid, SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import ItemBoolean from '../../../../../components/ItemBoolean';
import { useHelper } from '../../../../../store';
import { type PhishingEmailTemplate as PhishingEmailTemplateType } from '../../../../../utils/api-types';
import { emptyFilled } from '../../../../../utils/String';
import PhishingHtmlPreview from '../PhishingHtmlPreview';

const PhishingEmailTemplate = () => {
  const theme = useTheme();
  const { emailTemplateId } = useParams() as { emailTemplateId: PhishingEmailTemplateType['phishing_email_template_id'] };
  const { t } = useFormatter();
  const { emailTemplate } = useHelper(
    (helper: PhishingEmailTemplatesHelper) => ({ emailTemplate: helper.getPhishingEmailTemplate(emailTemplateId) as PhishingEmailTemplateType }),
  );

  const fromName = emailTemplate.phishing_email_template_from_name;
  const fromEmail = emailTemplate.phishing_email_template_from_email;
  const fromDisplay = (() => {
    if (fromName && fromEmail) {
      return `${fromName} <${fromEmail}>`;
    }
    if (fromName) {
      return fromName;
    }
    if (fromEmail) {
      return fromEmail;
    }
    return t('Platform default sender');
  })();

  // Light canvas + email-client chrome: lure HTML is authored for inbox
  // clients, which are almost always light. A dark paper would wash out text.
  const previewDocument = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:16px;background:#ffffff;color:#111111;font-family:Arial,Helvetica,sans-serif;}</style></head><body>${emailTemplate.phishing_email_template_html_body ?? ''}</body></html>`;

  const emailChrome = (
    <Box sx={{
      borderBottom: '1px solid rgba(0,0,0,0.08)',
      backgroundColor: '#f7f8fa',
      px: 2,
      py: 1.5,
      display: 'flex',
      flexDirection: 'column',
      gap: 0.75,
    }}
    >
      <Box sx={{
        display: 'flex',
        gap: 1,
        minWidth: 0,
        alignItems: 'baseline',
      }}
      >
        <Typography sx={{
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.06em',
          textTransform: 'uppercase',
          color: 'rgba(0,0,0,0.45)',
          width: 56,
          flexShrink: 0,
        }}
        >
          {t('From')}
        </Typography>
        <Typography sx={{
          fontSize: 13,
          color: 'rgba(0,0,0,0.87)',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        >
          {fromDisplay}
        </Typography>
      </Box>
      <Box sx={{
        display: 'flex',
        gap: 1,
        minWidth: 0,
        alignItems: 'baseline',
      }}
      >
        <Typography sx={{
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.06em',
          textTransform: 'uppercase',
          color: 'rgba(0,0,0,0.45)',
          width: 56,
          flexShrink: 0,
        }}
        >
          {t('Subject')}
        </Typography>
        <Typography sx={{
          fontSize: 13,
          fontWeight: 600,
          color: 'rgba(0,0,0,0.87)',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        >
          {emptyFilled(emailTemplate.phishing_email_template_subject)}
        </Typography>
      </Box>
    </Box>
  );

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
      paddingBottom: 40,
    }}
    >
      <DetailSections columns="minmax(320px, 1fr) minmax(420px, 1.45fr)">
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
        >
          <InformationGrid title={t('Configuration')} action={null}>
            <Field label={t('Subject')}>
              <Typography variant="body2">
                {emptyFilled(emailTemplate.phishing_email_template_subject)}
              </Typography>
            </Field>
            <Field label={t('Sender name override')}>
              <Typography variant="body2" sx={{ color: fromName ? 'text.primary' : 'text.secondary' }}>
                {emptyFilled(fromName)}
              </Typography>
            </Field>
            <Field label={t('Sender email override')}>
              <Typography variant="body2" sx={{ color: fromEmail ? 'text.primary' : 'text.secondary' }}>
                {emptyFilled(fromEmail)}
              </Typography>
            </Field>
            <Field label={t('Add tracking pixel')}>
              <ItemBoolean
                status={emailTemplate.phishing_email_template_add_tracking_pixel === true}
                label={emailTemplate.phishing_email_template_add_tracking_pixel ? t('Yes') : t('No')}
                variant="inList"
              />
            </Field>
          </InformationGrid>

          <SectionBlock title={t('Usage')}>
            <Box sx={{
              display: 'flex',
              gap: 1.5,
              alignItems: 'flex-start',
            }}
            >
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 32,
                height: 32,
                borderRadius: 1,
                flexShrink: 0,
                color: 'primary.main',
                backgroundColor: alpha(theme.palette.primary.main, 0.1),
              }}
              >
                <InfoOutlined sx={{ fontSize: 18 }} />
              </Box>
              <Typography
                variant="body2"
                sx={{
                  color: 'text.secondary',
                  lineHeight: 1.55,
                }}
              >
                {t('Use the {{phishing_url}} placeholder in the body to insert the per-recipient tracking link that leads to the landing page.')}
              </Typography>
            </Box>
          </SectionBlock>
        </div>

        <PhishingHtmlPreview
          title={t('Preview')}
          iframeTitle={emailTemplate.phishing_email_template_name ?? t('Preview')}
          srcDoc={previewDocument}
          chrome={emailChrome}
          height={560}
        />
      </DetailSections>
    </div>
  );
};

export default PhishingEmailTemplate;
