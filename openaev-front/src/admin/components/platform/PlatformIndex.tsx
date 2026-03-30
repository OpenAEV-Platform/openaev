import { Suspense } from 'react';
import { Routes } from 'react-router';

import Loader from '../payloads/Loader';
import TenantRoutes from './tenants/routes/TenantsRoutes';
import UsersCapabilitiesRoutes from './users_capabilities/routes/UsersCapabilitiesRoutes';

const PlatformIndex = () => {
  return (
    <Suspense fallback={<Loader />}>
      <Routes>
        {TenantRoutes}
        {UsersCapabilitiesRoutes}
      </Routes>
    </Suspense>
  );
};

export default PlatformIndex;
