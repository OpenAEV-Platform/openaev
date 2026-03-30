import { Suspense } from 'react';
import { Route, Routes } from 'react-router';

import Loader from '../../payloads/Loader';
import Tenants from './Tenants';

const TenantsIndex = () => {
  return (
    <Suspense fallback={<Loader />}>
      <Routes>
        <Route index element={<Tenants />} />
      </Routes>
    </Suspense>
  );
};

export default TenantsIndex;
