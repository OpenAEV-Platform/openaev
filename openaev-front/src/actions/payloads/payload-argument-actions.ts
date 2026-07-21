import { simpleCall } from '../../utils/Action';

const THREAT_ARSENAL_URI = '/api/threat_arsenals';

const fetchArgumentTypes = async (): Promise<string[]> => {
  const result = await simpleCall(`${THREAT_ARSENAL_URI}/argument-types/`);
  return result.data;
};

export default fetchArgumentTypes;
