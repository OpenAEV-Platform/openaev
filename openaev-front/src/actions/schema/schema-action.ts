import { simpleCall, simplePostCall } from '../../utils/Action';
import { type PropertySchemaDTO } from '../../utils/api-types';

export const filterableProperties = (clazz: string, filterNames: string[] = []) => {
  const uri = `/api/schemas/${clazz}?filterableOnly=${true}`;
  return simplePostCall<PropertySchemaDTO[]>(uri, filterNames);
};

export const engineSchemas = (classNames?: (string | undefined)[]) => {
  const params = new URLSearchParams();

  if (classNames && classNames.length > 0) {
    classNames.forEach((name) => {
      if (name) {
        params.append('classNames', name);
      }
    });
  }

  const uri = `/api/engine/schemas${params.toString() ? `?${params.toString()}` : ''}`;
  return simpleCall<PropertySchemaDTO[]>(uri);
};
