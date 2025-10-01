import { filterableProperties } from '../../../../actions/schema/schema-action';
import { type PropertySchemaDTO } from '../../../../utils/api-types';
import { convertJsonClassToJavaClass } from './FilterUtils';

const useFilterableProperties: (entityPrefix: string, filterNames: string[]) => Promise<PropertySchemaDTO[]> = (entity_prefix: string, filterNames: string[]) => {
  const javaClass = convertJsonClassToJavaClass(entity_prefix);
  return filterableProperties(javaClass, filterNames).then((result: { data: PropertySchemaDTO[] }) => {
    return result.data;
  });
};

export default useFilterableProperties;
