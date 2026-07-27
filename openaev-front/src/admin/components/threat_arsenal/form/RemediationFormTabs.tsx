import { Alert, Box, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useEffect, useState } from 'react';

import type { SecurityPlatformHelper } from '../../../../actions/assets/asset-helper';
import { fetchSecurityPlatforms } from '../../../../actions/assets/securityPlatform-actions';
import { fetchSecurityPlatformsForActionRemediation } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useHelper } from '../../../../store';
import { type SecurityPlatform } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import RestrictionAccess from '../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import InjectFormSection from '../../common/injects/form/InjectFormSection';
import RemediationFormTab from './RemediationFormTab';

interface RemediationFormTabsProps { actionId?: string }

// Remediation rules are keyed by security platform: every platform (manual ones
// included) can carry detection and remediation content, with no dependency on
// a registered collector.
const RemediationFormTabs = ({ actionId }: RemediationFormTabsProps) => {
  const [tabs, setTabs] = useState<SecurityPlatform[]>([]);
  const [activeTab, setActiveTab] = useState<number>(0);
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const [loading, setLoading] = useState(false);

  const handleActiveTabChange = (_: SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const hasSecurityPlatformsAccess = ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS);

  const { securityPlatforms }: { securityPlatforms: SecurityPlatform[] } = useHelper(
    (helper: SecurityPlatformHelper) => ({ securityPlatforms: helper.getSecurityPlatforms() }),
  );
  useDataLoader(() => {
    if (hasSecurityPlatformsAccess) {
      setLoading(true);
      dispatch(fetchSecurityPlatforms()).finally(() => {
        setLoading(false);
      });
    } else if (actionId) {
      setLoading(true);
      dispatch(fetchSecurityPlatformsForActionRemediation(actionId)).finally(() => {
        setLoading(false);
      });
    }
  });

  useEffect(() => {
    if (securityPlatforms.length > 0) {
      const sorted = [...securityPlatforms].sort(
        (a: SecurityPlatform, b: SecurityPlatform) => a.asset_name.localeCompare(b.asset_name),
      );
      setTabs(sorted);
    }
  }, [securityPlatforms]);

  useEffect(() => {
    if (activeTab >= tabs.length) {
      setActiveTab(0);
    }
  }, [tabs, activeTab]);

  if (!(hasSecurityPlatformsAccess || actionId)) {
    return <RestrictionAccess restrictedField="security platforms" />;
  }

  return (
    <InjectFormSection
      title={t('Security platforms')}
      helper={t('Document how each security platform detects and remediates this action.')}
    >
      {loading && <Loader variant="inElement" />}
      {!loading && tabs.length === 0 && (
        <Alert severity="info" variant="outlined">
          {t('No security platform configured yet. Create one (including manual platforms) to document detection and remediation rules.')}
        </Alert>
      )}
      {!loading && tabs.length > 0 && (
        <>
          <Tabs
            value={Math.min(activeTab, tabs.length - 1)}
            onChange={handleActiveTabChange}
            aria-label="tabs for remediation"
            variant="scrollable"
            scrollButtons="auto"
            allowScrollButtonsMobile
            sx={{
              'minHeight': 40,
              'borderBottom': `1px solid ${theme.palette.divider}`,
              '& .MuiTab-root': {
                // The theme forces `display: inline-block` + lowercase on MuiTab
                // for its `::first-letter` trick; restore the flex row so the
                // platform logo and name align, and keep the name capitalised.
                display: 'flex',
                flexDirection: 'row',
                alignItems: 'center',
                textTransform: 'none',
                minHeight: 40,
                gap: 1,
              },
            }}
          >
            {tabs.map((tab, index) => (
              <Tab
                key={tab.asset_id}
                label={(
                  <Box
                    display="flex"
                    alignItems="center"
                    gap={1}
                  >
                    <img
                      src={buildTenantApiPath(`/api/images/security_platforms/id/${tab.asset_id}/${theme.palette.mode}`)}
                      alt={tab.asset_name}
                      style={{
                        width: 18,
                        height: 18,
                        borderRadius: 4,
                      }}
                    />
                    {tab.asset_name}
                  </Box>
                )}
                value={index}
              />
            ))}
          </Tabs>
          {tabs[Math.min(activeTab, tabs.length - 1)] && (
            <RemediationFormTab
              key={'rem.' + tabs[Math.min(activeTab, tabs.length - 1)].asset_id}
              activeTab={tabs[Math.min(activeTab, tabs.length - 1)]}
            />
          )}
        </>
      )}
    </InjectFormSection>
  );
};

export default RemediationFormTabs;
