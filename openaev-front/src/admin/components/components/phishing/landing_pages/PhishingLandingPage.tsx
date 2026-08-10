import { InfoOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { useParams } from 'react-router';

import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import { Field, SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import ItemBoolean from '../../../../../components/ItemBoolean';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage as PhishingLandingPageType } from '../../../../../utils/api-types';
import { emptyFilled } from '../../../../../utils/String';
import PhishingHtmlPreview from '../PhishingHtmlPreview';

const PhishingLandingPage = () => {
  const { landingPageId } = useParams() as { landingPageId: PhishingLandingPageType['phishing_landing_page_id'] };
  const { t } = useFormatter();
  const { landingPage } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPage: helper.getPhishingLandingPage(landingPageId) as PhishingLandingPageType }),
  );

  const previewDocument = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{margin:0;padding:0;background:#ffffff;}</style><style>${landingPage.phishing_landing_page_css ?? ''}</style></head><body>${landingPage.phishing_landing_page_html ?? ''}</body></html>`;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
      paddingBottom: 40,
    }}
    >
      <SectionBlock title={t('Configuration')}>
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            sm: '1fr 1fr',
          },
          columnGap: 3,
          rowGap: 2.5,
        }}
        >
          <Field label={t('Description')}>
            <Typography
              variant="body2"
              sx={{ color: landingPage.phishing_landing_page_description ? 'text.primary' : 'text.secondary' }}
            >
              {emptyFilled(landingPage.phishing_landing_page_description)}
            </Typography>
          </Field>
          <Field label={t('Redirect URL after submit')}>
            <Typography
              variant="body2"
              sx={{
                wordBreak: 'break-all',
                color: landingPage.phishing_landing_page_redirect_url ? 'text.primary' : 'text.secondary',
              }}
            >
              {emptyFilled(landingPage.phishing_landing_page_redirect_url)}
            </Typography>
          </Field>
          <Field label={t('Capture submitted data')}>
            <ItemBoolean
              status={landingPage.phishing_landing_page_capture_submitted_data === true}
              label={landingPage.phishing_landing_page_capture_submitted_data ? t('Yes') : t('No')}
              variant="inList"
            />
          </Field>
          <Field label={t('Capture passwords')}>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.75,
            }}
            >
              <ItemBoolean
                status={landingPage.phishing_landing_page_capture_passwords === true}
                label={landingPage.phishing_landing_page_capture_passwords ? t('Yes') : t('No')}
                variant="inList"
              />
              <Tooltip title={t('When enabled, credentials submitted by recipients (username and password) are captured and tracked per target. When disabled, only the submission event is recorded - passwords are never stored.')}>
                <InfoOutlined sx={{
                  fontSize: 16,
                  color: 'text.secondary',
                  cursor: 'help',
                }}
                />
              </Tooltip>
            </Box>
          </Field>
        </Box>
      </SectionBlock>

      <PhishingHtmlPreview
        title={t('Preview')}
        iframeTitle={landingPage.phishing_landing_page_name ?? t('Preview')}
        srcDoc={previewDocument}
        height={620}
      />
    </div>
  );
};

export default PhishingLandingPage;
