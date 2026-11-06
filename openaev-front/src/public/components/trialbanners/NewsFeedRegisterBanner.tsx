import { useNavigate } from 'react-router';

import { useFormatter } from '../../../components/i18n';
import { REDIRECT_CONNECT_XTM_HUB_URL } from '../../../constants/BaseUrls';
import { useNewsFeedRegisterBanner } from './registerBannerState';
import TopBanner from './TopBanner';

/** Invites the admin of an unregistered tenant to connect the product to XTM Hub, which delivers the news feeds. */
const NewsFeedRegisterBanner = () => {
  const { t } = useFormatter();
  const navigate = useNavigate();
  const { isShown, dismiss } = useNewsFeedRegisterBanner();

  if (!isShown) return null;

  const text = (
    <>
      <strong>{t('New feeds are available.')}</strong>
      {' '}
      {t('Connect your product to access and deploy these feeds in one click.')}
    </>
  );

  return (
    <TopBanner
      bannerColor="gradient_blue"
      bannerText={text}
      buttonText={t('Connect Product')}
      buttonColor="blue-700"
      onButtonClick={() => navigate(REDIRECT_CONNECT_XTM_HUB_URL)}
      onDismiss={dismiss}
    />
  );
};

export default NewsFeedRegisterBanner;
