import { Navigate, Route, Routes } from 'react-router';

import { errorWrapper } from '../../../../components/Error';
import NotFound from '../../../../components/NotFound';
import PlatformGroups from './platform_groups/PlatformGroups';
import PlatformRoles from './platform_roles/PlatformRoles';
import PlatformUsers from './platform_users/PlatformUsers';
import {
  PLATFORM_GROUPS_PATH,
  PLATFORM_ROLES_PATH,
  PLATFORM_USERS_PATH,
} from './users-capabilities-constants';

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
