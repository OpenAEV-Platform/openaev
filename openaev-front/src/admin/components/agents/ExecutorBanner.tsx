import { type FunctionComponent } from 'react';

import { type ExecutorOutput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import { DEFAULT_TENANT_UUID } from '../../../utils/tenant-url-helper';

interface ExecutorBannerProps {
  executor: ExecutorOutput;
  height?: number;
}

const ExecutorBanner: FunctionComponent<ExecutorBannerProps> = ({ executor, height }) => {
  const { currentUserTenant } = useAuth();
  return (
    <div
      style={{
        backgroundColor: executor.executor_background_color,
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: height,
        overflow: 'hidden',
        position: 'relative',
        padding: 0,
      }}
    >
      <img
        src={`/api/tenants/${currentUserTenant?.tenant_id ?? DEFAULT_TENANT_UUID}/images/executors/banners/${executor.executor_type}`}
        alt={executor.executor_name}
        style={{ objectFit: 'cover' }}
      />
    </div>
  );
};

export default ExecutorBanner;
