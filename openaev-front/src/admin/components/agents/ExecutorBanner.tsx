import { type FunctionComponent } from 'react';

import { type Executor } from '../../../utils/api-types';

interface ExecutorBannerProps {
  executor: Executor;
  height?: number;
}

const ExecutorBanner: FunctionComponent<ExecutorBannerProps> = ({ executor, height }) => {
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
        src={`/api/images/executors/banners/${executor.executor_type}`}
        alt={executor.executor_name}
        style={{ objectFit: 'cover' }}
      />
    </div>
  );
};

export default ExecutorBanner;
