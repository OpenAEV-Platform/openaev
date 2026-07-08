import { simpleCall } from '../../utils/Action';
import { type PrimitiveTypeOutput } from '../../utils/api-types';

const THREAT_ARSENAL_URI = '/api/threat_arsenals';

const fetchArgumentTypes = async (): Promise<PrimitiveTypeOutput['argument_type'][]> => {
  const result = await simpleCall(`${THREAT_ARSENAL_URI}/argument-types/`);
  return result.data;
};

export default fetchArgumentTypes;
