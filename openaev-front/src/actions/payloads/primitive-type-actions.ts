import { simpleCall } from '../../utils/Action';
import { type PrimitiveTypeDescriptorOutput } from '../../utils/api-types';

const THREAT_ARSENAL_URI = '/api/threat_arsenals';

/**
 * Operator capabilities and value format rules of every primitive type.
 *
 * Static, tenant-independent metadata: it only describes the shape of a value, so it is safe to
 * fetch once and share across the whole session.
 */
export const fetchPrimitiveTypeDescriptors = async (): Promise<PrimitiveTypeDescriptorOutput[]> => {
  const result = await simpleCall(`${THREAT_ARSENAL_URI}/primitive-types`);
  return result.data;
};

export default fetchPrimitiveTypeDescriptors;
