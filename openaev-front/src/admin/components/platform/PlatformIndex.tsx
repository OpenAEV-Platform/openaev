import { Suspense } from 'react';
import { Routes } from 'react-router';

import Loader from '../payloads/Loader';
import TenantRoutes from './tenants/routes/TenantsRoutes';

const PlatformIndex = () => {
  return (
    <Suspense fallback={<Loader />}>
      <Routes>
        {TenantRoutes}
      </Routes>
    </Suspense>
  );
};

export default PlatformIndex;
