import { Paper, Typography } from '@mui/material';
import { useParams } from 'react-router';

import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage as PhishingLandingPageType } from '../../../../../utils/api-types';

const PhishingLandingPage = () => {
  const { landingPageId } = useParams() as { landingPageId: PhishingLandingPageType['phishing_landing_page_id'] };
  const { t } = useFormatter();
  const { landingPage } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPage: helper.getPhishingLandingPage(landingPageId) as PhishingLandingPageType }),
  );

  const previewDocument = `<!doctype html><html><head><style>${landingPage.phishing_landing_page_css ?? ''}</style></head><body>${landingPage.phishing_landing_page_html ?? ''}</body></html>`;

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
          <Typography variant="h3" gutterBottom>{t('Capture submitted data')}</Typography>
          {landingPage.phishing_landing_page_capture_submitted_data ? t('Yes') : t('No')}
          <Typography variant="h3" gutterBottom sx={{ marginTop: 2 }}>{t('Capture passwords')}</Typography>
          {landingPage.phishing_landing_page_capture_passwords ? t('Yes') : t('No')}
          <Typography variant="h3" gutterBottom sx={{ marginTop: 2 }}>{t('Redirect URL after submit')}</Typography>
          {landingPage.phishing_landing_page_redirect_url || '-'}
          <Typography
            variant="body2"
            sx={{
              marginTop: 2,
              color: 'text.secondary',
            }}
          >
            {t('This landing page is available as a phishing action in the Threat Arsenal. Add it to an inject to launch a phishing campaign targeting teams and players.')}
          </Typography>
        </Paper>
      </div>
      <div style={{ flex: 1 }}>
        <Typography variant="h4" gutterBottom>{t('Preview')}</Typography>
        <Paper variant="outlined" sx={{ padding: 0 }}>
          <iframe
            title={landingPage.phishing_landing_page_name}
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

export default PhishingLandingPage;
