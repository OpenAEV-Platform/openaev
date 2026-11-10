import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { useLocalStorage } from 'usehooks-ts';

import { type LoggedHelper } from '../../../actions/helper';
import { fetchXtmHubRegistration } from '../../../actions/xtmhub/xtmhub-actions';
import { useHelper } from '../../../store';
import { useAppDispatch } from '../../../utils/hooks';
import useAuth from '../../../utils/hooks/useAuth';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';
import {
  isRegistered,
  NewsFeedRegisterBannerContext,
  REGISTER_BANNER_DISMISSED_KEY_PREFIX,
} from './registerBannerState';

interface Props { children: ReactNode }

/**
 * Decides once whether the banner inviting to connect the product to XTM Hub is displayed, so that the banner
 * and the height the layout reserves for it cannot disagree. Shown to someone who can follow it (same
 * permission as the connect flow) on a tenant that is not registered, while XTM Hub is reachable from both the
 * browser and the backend. Stays hidden while the registration is unknown (not fetched yet, or the lookup failed).
 */
const NewsFeedRegisterBannerProvider = ({ children }: Props) => {
  const dispatch = useAppDispatch();
  const ability = useAbility();
  const { settings, isXTMHubAccessible, userTenants, currentUserTenant } = useAuth();
  const registration = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());
  const [dismissed, setDismissed] = useLocalStorage<boolean>(
    `${REGISTER_BANNER_DISMISSED_KEY_PREFIX}-${currentUserTenant?.tenant_id ?? 'default'}`,
    false,
  );
  // The store holds no registration before the answer, after a failed lookup and for a tenant that is not
  // registered: without this, a registered tenant would see the banner while the registration is unknown.
  const [loaded, setLoaded] = useState(false);

  const flagEnabled = isFeatureEnabled('XTM_HUB_NEWS_FEED');
  const canConnect = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const eligible = flagEnabled && canConnect;
  // Tenant initialization dispatches TENANT_SWITCH_SUCCESS, which clears the tenant-scoped store: a registration
  // fetched before it would be lost. An empty list still counts as initialized (platform admin without membership).
  const tenantInitialized = userTenants !== undefined;
  const tenantId = currentUserTenant?.tenant_id;

  useEffect(() => {
    setLoaded(false);
    if (!eligible || !tenantInitialized) {
      return undefined;
    }
    let cancelled = false;
    Promise.resolve(dispatch(fetchXtmHubRegistration())).then((known) => {
      if (!cancelled && known) {
        setLoaded(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [eligible, tenantInitialized, tenantId]);

  const registered = isRegistered(registration);

  // Forget the dismissal once registered, so the banner comes back if the tenant is unregistered later.
  useEffect(() => {
    if (registered && dismissed) {
      setDismissed(false);
    }
  }, [registered, dismissed]);

  // Same condition as the connect flow in the settings: the browser and the backend must both reach XTM Hub.
  const hubReachable = isXTMHubAccessible === true && settings.xtm_hub_reachable === true;
  const isShown = eligible && loaded && hubReachable && !registered && !dismissed;

  const value = useMemo(() => ({
    isShown,
    dismiss: () => setDismissed(true),
  }), [isShown, setDismissed]);

  return (
    <NewsFeedRegisterBannerContext.Provider value={value}>
      {children}
    </NewsFeedRegisterBannerContext.Provider>
  );
};

export default NewsFeedRegisterBannerProvider;
