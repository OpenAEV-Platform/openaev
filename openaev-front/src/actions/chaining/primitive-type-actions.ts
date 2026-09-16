import { simpleCall } from '../../utils/Action';
import { type PrimitiveTypeDescriptorOutput } from '../../utils/api-types';

const CHAINING_URI = '/api/chaining';

/**
 * Operator capabilities and value format rules of every primitive chaining type.
 *
 * Static, tenant-independent metadata: it only describes the shape of a value, so it is safe to
 * fetch once and share across the whole session.
 */
export const fetchPrimitiveTypeDescriptors = async (): Promise<PrimitiveTypeDescriptorOutput[]> => {
  const result = await simpleCall(`${CHAINING_URI}/primitive-types`);
  return result.data;
};

export default fetchPrimitiveTypeDescriptors;
