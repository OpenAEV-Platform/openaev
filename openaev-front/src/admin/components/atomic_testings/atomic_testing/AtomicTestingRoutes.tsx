import { lazy } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router';

import { errorWrapper } from '../../../../components/Error';
import NotFound from '../../../../components/NotFound';
import type { InjectResultOverviewOutput } from '../../../../utils/api-types';
import { externalContractTypesWithFindings } from '../../../../utils/injector_contract/InjectorContractUtils';
import SnapshotRemediationProvider from '../../threat_arsenal/utils/SnapshotRemediationProvider';

interface Props { injectResultOverview: InjectResultOverviewOutput }

const AtomicTesting = lazy(() => import('./AtomicTesting'));
const AtomicTestingExecutionDetails = lazy(() => import('./AtomicTestingExecutionDetails'));
const AtomicTestingFindings = lazy(() => import('./AtomicTestingFindings'));
const AtomicTestingPayloadInfo = lazy(() => import('./payload_info/AtomicTestingPayloadInfo'));
const AtomicTestingRemediations = lazy(() => import('./AtomicTestingRemediations'));

const AtomicTestingRoutes = ({ injectResultOverview }: Props) => {
  const location = useLocation();

  return (
    <SnapshotRemediationProvider>
      <Routes>
        <Route
          path=""
          element={injectResultOverview.inject_has_targets
            ? errorWrapper(AtomicTesting)()
            : <Navigate to={`execution_details${location.search}`} replace />}
        />
        <Route path="execution_details" element={errorWrapper(AtomicTestingExecutionDetails)()} />
        {(injectResultOverview.inject_injector_contract?.injector_contract_payload
          || externalContractTypesWithFindings.includes(injectResultOverview.inject_type ?? '')) && (
          <Route path="findings" element={errorWrapper(AtomicTestingFindings)()} />
        )}
        {injectResultOverview.inject_injector_contract?.injector_contract_payload && (
          <>
            <Route path="payload_info" element={errorWrapper(AtomicTestingPayloadInfo)()} />
            <Route path="remediations" element={errorWrapper(AtomicTestingRemediations)()} />
          </>
        )}
        <Route path="*" element={<NotFound />} />
      </Routes>
    </SnapshotRemediationProvider>
  );
};
export default AtomicTestingRoutes;
