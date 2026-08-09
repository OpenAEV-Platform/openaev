import { PublicOutlined } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useParams } from 'react-router';

import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import { DetailHero } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage } from '../../../../../utils/api-types';
import PhishingLandingPagePopover from './PhishingLandingPagePopover';

const PhishingLandingPageHeader = () => {
  const { landingPageId } = useParams() as { landingPageId: PhishingLandingPage['phishing_landing_page_id'] };
  const { t } = useFormatter();
  const { landingPage } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPage: helper.getPhishingLandingPage(landingPageId) as PhishingLandingPage }),
  );

  return (
    <DetailHero
      iconNode={<PublicOutlined />}
      overline={t('Phishing landing page')}
      title={landingPage.phishing_landing_page_name ?? '-'}
      action={<PhishingLandingPagePopover landingPage={landingPage} />}
      footer={landingPage.phishing_landing_page_description
        ? (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {landingPage.phishing_landing_page_description}
            </Typography>
          )
        : undefined}
    />
  );
};

export default PhishingLandingPageHeader;
