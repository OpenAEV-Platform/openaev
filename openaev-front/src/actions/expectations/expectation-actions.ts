import { simpleCall } from '../../utils/Action';

const EXPECTATIONS_URI = '/api/injects/expectations';

const availableExpectationsForInjectorContract = (injectorContractId: string = '') => {
  const params = { injectorContractId };
  const uri = `${EXPECTATIONS_URI}/available`;
  return simpleCall(uri, { params });
};
export default availableExpectationsForInjectorContract;
