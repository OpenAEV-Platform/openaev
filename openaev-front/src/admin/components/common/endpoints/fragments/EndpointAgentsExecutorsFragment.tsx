import { Tooltip } from '@mui/material';

import { fetchExecutors } from '../../../../../actions/executors/executor-action';
import type { ExecutorHelper } from '../../../../../actions/executors/executor-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type EndpointOutput } from '../../../../../utils/api-types';
import { getExecutorsCount } from '../../../../../utils/endpoints/utils';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';

type Props = { endpoint: EndpointOutput };

const EndpointActiveFragment = (props: Props) => {
  const dispatch = useAppDispatch();
  // Fetching data
  const { executorsMap } = useHelper((helper: ExecutorHelper) => ({ executorsMap: helper.getExecutorsMap() }));
  useDataLoader(() => {
    dispatch(fetchExecutors());
  });
  const { t } = useFormatter();
  if (props.endpoint.asset_agents.length <= 0) {
    return <span>-</span>;
  }

  const groupedExecutors = getExecutorsCount(props.endpoint, executorsMap);
  if (!groupedExecutors) {
    return '-';
  }

  return (
    <>
      {
        Object.keys(groupedExecutors).map((executorType) => {
          const executorsOfType = groupedExecutors[executorType];
          const count = executorsOfType.length;
          const base = executorsOfType[0];

          if (count > 0) {
            return (
              <Tooltip key={executorType} title={`${base.executor_name} : ${count}`} arrow>
                <div style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                }}
                >
                  <img
                    src={`/api/images/executors/icons/${executorType}`}
                    alt={executorType}
                    style={{
                      width: 20,
                      height: 20,
                      borderRadius: 4,
                      marginRight: 10,
                    }}
                  />
                </div>
              </Tooltip>
            );
          } else {
            return t('Unknown');
          }
        })
      }
    </>
  );
};

export default EndpointActiveFragment;
