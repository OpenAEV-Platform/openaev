import { useEffect, useState } from 'react';

import { PAYLOAD_TYPE_LIST_AI } from '../../../../constants/Entities';

const useIsEligibleArianePayloadType = (payloadType: string | undefined) => {
  const [isEligibleAriane, setIsEligibleAriane] = useState(false);

  useEffect(() => {
    setIsEligibleAriane(payloadType ? PAYLOAD_TYPE_LIST_AI.includes(payloadType) : true);
  }, [payloadType]);

  return isEligibleAriane;
};

export default useIsEligibleArianePayloadType;
