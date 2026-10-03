import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { useContext } from 'react';
import { Link, useLocation } from 'react-router';

import { useFormatter } from '../../../components/i18n';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { IOC_VALIDATION_BASE_URL } from '../ioc_validations/iocValidationUtils';

const ATOMIC_TESTINGS_BASE_URL = '/admin/atomic_testings';

const AtomicTestingsTabs = () => {
  const { t } = useFormatter();
  const location = useLocation();
  const ability = useContext(AbilityContext);

  const tabValue = location.pathname;
  const current = (path: string) => (tabValue === path ? 'page' : undefined);

  // Route-based tabs: the panels are the routed pages, so every tab is a real link.
  return (
    <Tabs value={tabValue} panels="external">
      <TabsList>
        <TabsTrigger value={ATOMIC_TESTINGS_BASE_URL} asChild>
          <Link to={ATOMIC_TESTINGS_BASE_URL} aria-current={current(ATOMIC_TESTINGS_BASE_URL)}>{t('Atomic testings')}</Link>
        </TabsTrigger>
        {ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT) && (
          <TabsTrigger value={IOC_VALIDATION_BASE_URL} asChild>
            <Link to={IOC_VALIDATION_BASE_URL} aria-current={current(IOC_VALIDATION_BASE_URL)}>{t('IOC validations')}</Link>
          </TabsTrigger>
        )}
      </TabsList>
    </Tabs>
  );
};

export default AtomicTestingsTabs;
