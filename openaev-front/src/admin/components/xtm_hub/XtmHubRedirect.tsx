import { useContext } from 'react';
import { Navigate, useLocation, useParams } from 'react-router';

import NotFound from '../../../components/NotFound';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';

export const XTM_HUB_AUTO_REGISTER_QUERY_PARAM = 'xtmHubAutoRegister';
export const XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY = 'xtmHubPermissionRequired';

const STATIC_PATH_REDIRECTS: Record<string, string> = { 'connect-xtm-hub': '/admin/settings/experience' };

const PATH_REDIRECT_QUERY_PARAMS: Record<string, Record<string, string>> = { 'connect-xtm-hub': { [XTM_HUB_AUTO_REGISTER_QUERY_PARAM]: 'true' } };

const normalizePathKey = (value?: string) => (value ?? '').replace(/^\/+|\/+$/g, '');

const XtmHubRedirect = () => {
  const { '*': pathKey } = useParams();
  const { search } = useLocation();
  const ability = useContext(AbilityContext);
  const normalizedPathKey = normalizePathKey(pathKey);
  const targetPath = STATIC_PATH_REDIRECTS[normalizedPathKey];
  if (!targetPath) {
    return <NotFound />;
  }

  const searchParams = new URLSearchParams(search);
  const canManageTenantSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  if (normalizedPathKey === 'connect-xtm-hub' && !canManageTenantSettings) {
    sessionStorage.setItem(XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY, 'true');
    return (
      <Navigate
        to={{
          pathname: '/admin',
          search,
        }}
        replace={true}
      />
    );
  }

  const extraParams = PATH_REDIRECT_QUERY_PARAMS[normalizedPathKey] ?? {};
  Object.entries(extraParams).forEach(([key, value]) => searchParams.set(key, value));
  const targetSearch = searchParams.toString();

  return (
    <Navigate
      to={{
        pathname: targetPath,
        search: targetSearch ? `?${targetSearch}` : undefined,
      }}
      replace={true}
    />
  );
};

export default XtmHubRedirect;
