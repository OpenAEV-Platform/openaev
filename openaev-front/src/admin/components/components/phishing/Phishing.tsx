import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { lazy, Suspense } from 'react';
import { Link, Navigate, useParams } from 'react-router';

import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';

const PhishingLandingPages = lazy(() => import('./landing_pages/PhishingLandingPages'));
const PhishingEmailTemplates = lazy(() => import('./email_templates/PhishingEmailTemplates'));

const TABS = ['landing_pages', 'email_templates'] as const;
type PhishingTab = typeof TABS[number];

/**
 * The single "Phishing" components page: one left-menu entry, two tabs.
 * "Pages" lists the reusable landing pages, "Emails" the reusable lure email
 * templates. The active tab lives in the URL (phishing/landing_pages,
 * phishing/email_templates) so existing deep links and detail routes keep
 * working unchanged.
 */
const Phishing = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { tab } = useParams() as { tab?: string };

  if (!tab || !TABS.includes(tab as PhishingTab)) {
    return <Navigate to="/admin/components/phishing/landing_pages" replace />;
  }
  const activeTab = tab as PhishingTab;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      {/* Zero the list-variant marginBottom: the page gap already spaces the
          breadcrumb from the tabs, so keeping both stacked too much empty room. */}
      <Breadcrumbs
        variant="list"
        style={{ marginBottom: 0 }}
        elements={[{ label: t('Components') }, {
          label: t('Phishing'),
          current: true,
        }]}
      />
      <Tabs value={activeTab} panels="external">
        <TabsList>
          <TabsTrigger value="landing_pages" asChild>
            <Link to="/admin/components/phishing/landing_pages" aria-current={activeTab === 'landing_pages' ? 'page' : undefined}>
              {t('Pages')}
            </Link>
          </TabsTrigger>
          <TabsTrigger value="email_templates" asChild>
            <Link to="/admin/components/phishing/email_templates" aria-current={activeTab === 'email_templates' ? 'page' : undefined}>
              {t('Emails')}
            </Link>
          </TabsTrigger>
        </TabsList>
      </Tabs>
      <Suspense fallback={<Loader variant="inElement" />}>
        {activeTab === 'landing_pages' ? <PhishingLandingPages /> : <PhishingEmailTemplates />}
      </Suspense>
    </div>
  );
};

export default Phishing;
