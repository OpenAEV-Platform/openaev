import { simpleCall } from '../../utils/Action';

const EXPECTATIONS_URI = '/api/injects/expectations';

const availableExpectationsForInject = (isHumanInject: boolean) => {
  const params = { isHumanInject };
  const uri = `${EXPECTATIONS_URI}/available`;
  return simpleCall(uri, { params });
};
export default availableExpectationsForInject;
