import { useEffect, useState } from 'react';

import { COLLECTOR_LIST_AI } from '../../../../constants/Entities';

const useIsEligibleArianeCollector = (collectorType: string) => {
  const [isEligibleAriane, setIsEligibleAriane] = useState(false);

  useEffect(() => {
    setIsEligibleAriane(collectorType ? COLLECTOR_LIST_AI.includes(collectorType) : false);
  }, [collectorType]);

  return isEligibleAriane;
};

export default useIsEligibleArianeCollector;
