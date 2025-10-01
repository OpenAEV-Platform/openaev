import { type FunctionComponent, useEffect, useState } from 'react';

import { getAlertLinksCount } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { type InjectExpectationResult } from '../../../../../utils/api-types';

interface Props {
  injectExpectationId: string;
  expectationResult: InjectExpectationResult;
}

const TargetResultAlertNumber: FunctionComponent<Props> = ({
  injectExpectationId,
  expectationResult,
}) => {
  const [alertLinksNumber, setAlertLinksNumber] = useState<number | null>(0);

  useEffect(() => {
    getAlertLinksCount(injectExpectationId, expectationResult.sourceId, expectationResult.sourceType).then((result: { data: number }) => setAlertLinksNumber(result.data ?? 0));
  }, [injectExpectationId, expectationResult.sourceId]);

  return (
    <div>
      {alertLinksNumber}
    </div>
  );
};

export default TargetResultAlertNumber;
