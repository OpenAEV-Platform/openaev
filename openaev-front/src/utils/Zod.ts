import { setIn } from 'final-form';
import { z, type ZodError, type ZodType } from 'zod';

export const PHONE_REGEX = /^\+[\d\s\-.()]+$/;

type ZodImplements<Model> = {
  [key in keyof Model]-?: undefined extends Model[key]
    ? null extends Model[key]
      ? z.ZodNullable<z.ZodOptional<z.ZodType<Model[key]>>>
      : z.ZodOptional<z.ZodType<Model[key]>>
    : null extends Model[key]
      ? z.ZodNullable<z.ZodType<Model[key]>>
      : z.ZodType<Model[key]>;
};

// TextFieldController stores its value as a string, even with type="number": coerce it back to a
// number before validation, an emptied input becoming `undefined` so it fails the required check.
// 0 is a valid value (priorities and orders can be 0).
export const requiredNumber = (message: string) => z.preprocess(
  value => (value === '' || value === null ? undefined : value),
  z.coerce.number({ error: () => message }),
) as unknown as z.ZodType<number, number>;

export function zodImplement<Model = never>() {
  return {
    with: <
      Schema extends ZodImplements<Model> & {
        [unknownKey in Exclude<keyof Schema, keyof Model>]: never;
      },
    >(
      schema: Schema,
    ) => z.object(schema),
  };
}

export const schemaValidator = (schema: ZodType<unknown>) => (values: unknown) => {
  try {
    schema.parse(values);
  } catch (e) {
    return (e as ZodError).issues.reduce((errors, error) => {
      let path = '';
      if (error.path) {
        path = error.path[0].toString();
      }
      return setIn(errors, path, error.message);
    }, {});
  }
  return {};
};
