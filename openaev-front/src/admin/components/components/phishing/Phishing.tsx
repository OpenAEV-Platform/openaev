import { Tab, Tabs } from '@mui/material';
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
      <Tabs
        value={activeTab}
        sx={{
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Tab
          component={Link}
          to="/admin/components/phishing/landing_pages"
          value="landing_pages"
          label={t('Pages')}
        />
        <Tab
          component={Link}
          to="/admin/components/phishing/email_templates"
          value="email_templates"
          label={t('Emails')}
        />
      </Tabs>
      <Suspense fallback={<Loader variant="inElement" />}>
        {activeTab === 'landing_pages' ? <PhishingLandingPages /> : <PhishingEmailTemplates />}
      </Suspense>
    </div>
  );
};

export default Phishing;
