import { Alert, Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { lazy, Suspense, useState } from 'react';
import { Navigate, useParams } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { fetchCatalogConnectors } from '../../../actions/catalog/catalog-actions';
import { type CatalogConnectorsHelper } from '../../../actions/catalog/catalog-helper';
import MarketplaceHero from '../../../components/common/marketplace/page/MarketplaceHero';
import MarketplaceTabs from '../../../components/common/marketplace/page/MarketplaceTabs';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { useHelper } from '../../../store';
import { type CatalogConnectorOutput } from '../../../utils/api-types';
import docsUrl from '../../../utils/docsUrl';
import { useAppDispatch } from '../../../utils/hooks';
import useDataLoader from '../../../utils/hooks/useDataLoader';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import useXtmComposerReachable from './common/useXtmComposerReachable';

const Catalog = lazy(() => import('./catalog_connectors/Catalog'));
const DeployedConnectors = lazy(() => import('./deployed/DeployedConnectors'));

const TABS = ['deployed', 'available'] as const;
type IntegrationsTab = typeof TABS[number];

const INTEGRATION_MANAGER_WARNING_DISMISSED = 'integration_manager_warning_dismissed';

/**
 * The single integrations page: one hero, two tabs. "Deployed" lists every
 * connector currently registered on the platform, "Available" is the full
 * catalog marketplace. Both tabs share the same faceted browser.
 */
const Integrations = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { tab } = useParams() as { tab?: string };

  const [loading, setLoading] = useState<boolean>(true);
  const isXtmComposerUp = useXtmComposerReachable();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const [warningDismissed, setWarningDismissed] = useLocalStorage<boolean>(INTEGRATION_MANAGER_WARNING_DISMISSED, false);

  const { catalogConnectors }: { catalogConnectors: CatalogConnectorOutput[] } = useHelper(
    (helper: CatalogConnectorsHelper) => ({ catalogConnectors: helper.getCatalogConnectors() }),
  );

  useDataLoader(() => {
    dispatch(fetchCatalogConnectors()).finally(() => setLoading(false));
  });

  if (!tab || !TABS.includes(tab as IntegrationsTab)) {
    return <Navigate to="../deployed" replace />;
  }
  const activeTab = tab as IntegrationsTab;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(3),
    }}
    >
      {isEnterpriseEdition && isXtmComposerUp === false && !warningDismissed
        && (
          <Alert
            severity="warning"
            title={(
              <>
                {t('Some deployment requires the installation of our')}
                &nbsp;
                <a
                  href={docsUrl('integration_manager')}
                  target="_blank"
                  rel="noreferrer"
                >
                  {t('Integration Manager')}
                </a>
              </>
            )}
            action={<Button type="button" priority="secondary" size="sm" onClick={() => setWarningDismissed(true)}>{t('Dismiss')}</Button>}
          />
        )}
      <MarketplaceHero
        title={t('Integrations')}
        subtitle={t('Browse, filter and deploy collectors, injectors and executors from the XTM ecosystem.')}
      />
      <MarketplaceTabs
        active={activeTab}
        tabs={[
          {
            value: 'deployed',
            label: t('Active'),
            to: '/admin/integrations/deployed',
          },
          {
            value: 'available',
            label: t('Available'),
            to: '/admin/integrations/available',
          },
        ]}
      />
      {loading && <Loader variant="inElement" />}
      {!loading && (
        <Suspense fallback={<Loader variant="inElement" />}>
          {activeTab === 'available'
            ? <Catalog catalogConnectors={catalogConnectors} isXtmComposerUp={isXtmComposerUp === true} />
            : <DeployedConnectors catalogConnectors={catalogConnectors} isXtmComposerUp={isXtmComposerUp === true} />}
        </Suspense>
      )}
    </div>
  );
};

export default Integrations;
