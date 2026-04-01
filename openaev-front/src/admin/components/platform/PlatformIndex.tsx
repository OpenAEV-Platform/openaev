import { Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';

import Loader from '../payloads/Loader';
import TenantRoutes from './tenants/routes/TenantsRoutes';
import UsersCapabilitiesRoutes from './users_capabilities/routes/UsersCapabilitiesRoutes';

const PlatformIndex = () => {
  return (
    <Suspense fallback={<Loader />}>
      <Routes>
        <Route index element={<Navigate to="tenants" replace />} />
        {TenantRoutes}
        {UsersCapabilitiesRoutes}
      </Routes>
    </Suspense>
  );
};

export default PlatformIndex;
