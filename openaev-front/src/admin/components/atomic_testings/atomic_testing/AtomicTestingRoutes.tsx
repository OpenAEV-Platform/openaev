import { lazy } from 'react';
import { Route, Routes } from 'react-router';

import { errorWrapper } from '../../../../components/Error';
import NotFound from '../../../../components/NotFound';
import type { InjectResultOverviewOutput } from '../../../../utils/api-types';
import { externalContractTypesWithFindings } from '../../../../utils/injector_contract/InjectorContractUtils';
import SnapshotRemediationProvider from '../../payloads/utils/SnapshotRemediationProvider';

interface Props { injectResultOverview: InjectResultOverviewOutput }

const AtomicTesting = lazy(() => import('./AtomicTesting'));
const AtomicTestingDetail = lazy(() => import('./AtomicTestingDetail'));
const AtomicTestingFindings = lazy(() => import('./AtomicTestingFindings'));
const AtomicTestingPayloadInfo = lazy(() => import('./payload_info/AtomicTestingPayloadInfo'));
const AtomicTestingRemediations = lazy(() => import('./AtomicTestingRemediations'));

const AtomicTestingRoutes = ({ injectResultOverview }: Props) => {
  return (
    <SnapshotRemediationProvider>
      <Routes>
        <Route path="" element={errorWrapper(AtomicTesting)()} />
        {(injectResultOverview.inject_injector_contract?.injector_contract_payload
          || externalContractTypesWithFindings.includes(injectResultOverview.inject_type ?? '')) && (
          <Route path="findings" element={errorWrapper(AtomicTestingFindings)()} />
        )}
        <Route path="detail" element={errorWrapper(AtomicTestingDetail)()} />
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
