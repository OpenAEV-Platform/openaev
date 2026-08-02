import { lazy } from 'react';
import { Route, Routes, useParams } from 'react-router';

import { fetchPhishingLandingPage } from '../../../../../actions/phishing/phishing-action';
import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import Breadcrumbs from '../../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../../components/Error';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import NotFound from '../../../../../components/NotFound';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage as PhishingLandingPageType } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import PhishingLandingPageHeader from './PhishingLandingPageHeader';

const PhishingLandingPage = lazy(() => import('./PhishingLandingPage'));

const Index = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { landingPageId } = useParams() as { landingPageId: PhishingLandingPageType['phishing_landing_page_id'] };
  const { landingPage } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPage: helper.getPhishingLandingPage(landingPageId) }),
  );
  useDataLoader(() => {
    dispatch(fetchPhishingLandingPage(landingPageId));
  });
  if (landingPage) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
      >
        <Breadcrumbs
          variant="object"
          elements={[
            { label: t('Components') },
            {
              label: t('Phishing pages'),
              link: '/admin/components/phishing/landing_pages',
            },
            {
              label: landingPage.phishing_landing_page_name,
              current: true,
            },
          ]}
        />
        <PhishingLandingPageHeader />
        <Routes>
          <Route path="" element={errorWrapper(PhishingLandingPage)()} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </div>
    );
  }
  return <Loader />;
};

export default Index;
