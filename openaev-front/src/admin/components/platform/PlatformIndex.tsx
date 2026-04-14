import { Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';

import Loader from '../payloads/Loader';
import SettingsRoutes, { SETTINGS_PATH } from './settings/routes/SettingsRoutes';
import TenantRoutes from './tenants/routes/TenantsRoutes';
import UsersCapabilitiesRoutes from './users_capabilities/routes/UsersCapabilitiesRoutes';

const PlatformIndex = () => {
  return (
    <Suspense fallback={<Loader />}>
      <Routes>
        <Route index element={<Navigate to={SETTINGS_PATH} replace />} />
        {SettingsRoutes}
        {TenantRoutes}
        {UsersCapabilitiesRoutes}
      </Routes>
    </Suspense>
  );
};

export default PlatformIndex;
