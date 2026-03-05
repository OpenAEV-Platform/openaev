import { Navigate, Route, Routes } from 'react-router';

import { errorWrapper } from '../../../../components/Error';
import NotFound from '../../../../components/NotFound';
import { PLATFORM_USERS_CAPABILITIES_ROUTE } from '../../nav/config/platform.config';
import PlatformGroups from './platform_groups/PlatformGroups';
import PlatformRoles from './platform_roles/PlatformRoles';
import PlatformUsers from './platform_users/PlatformUsers';

// Routes
export const PLATFORM_ROLES_PATH = 'roles';
export const PLATFORM_ROLES_ROUTE = `${PLATFORM_USERS_CAPABILITIES_ROUTE}/${PLATFORM_ROLES_PATH}`;
export const PLATFORM_GROUPS_PATH = 'groups';
export const PLATFORM_GROUPS_ROUTE = `${PLATFORM_USERS_CAPABILITIES_ROUTE}/${PLATFORM_GROUPS_PATH}`;
export const PLATFORM_USERS_PATH = 'users';
export const PLATFORM_USERS_ROUTE = `${PLATFORM_USERS_CAPABILITIES_ROUTE}/${PLATFORM_USERS_PATH}`;

const UsersCapabilitiesIndex = () => {
  return (
    <Routes>
      <Route path="" element={<Navigate to={PLATFORM_ROLES_PATH} replace={true} />} />
      <Route path={PLATFORM_ROLES_PATH} element={errorWrapper(PlatformRoles)()} />
      <Route path={PLATFORM_GROUPS_PATH} element={errorWrapper(PlatformGroups)()} />
      <Route path={PLATFORM_USERS_PATH} element={errorWrapper(PlatformUsers)()} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};

export default UsersCapabilitiesIndex;

