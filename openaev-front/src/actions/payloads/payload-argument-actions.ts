import { simpleCall } from '../../utils/Action';
import { type ArgumentTypeOutput } from '../../utils/api-types';

const PAYLOAD_ARGUMENTS_URI = '/api/payload-arguments';

const fetchArgumentTypes = async (): Promise<ArgumentTypeOutput[]> => {
  const result = await simpleCall(`${PAYLOAD_ARGUMENTS_URI}/types`);
  return result.data;
};

export default fetchArgumentTypes;
