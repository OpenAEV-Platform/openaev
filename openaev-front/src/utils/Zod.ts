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

type Translate = (message: string) => string;

// TextFieldController stores its value as a string, even with type="number": coerce it back to a
// number before validation, an emptied (or blank) input becoming `undefined` so it fails the
// required check. Priorities and orders are integers stored as is, 0 included.
export const requiredInteger = (t: Translate) => z.preprocess(
  value => (value === null || (typeof value === 'string' && value.trim() === '') ? undefined : value),
  z.coerce
    .number({ error: () => t('This field is required.') })
    .int({ error: t('Must be a whole number') })
    .min(0, { error: t('Must be greater than or equal to 0') }),
) as unknown as z.ZodType<number, number>;

// An emptied text input holds '': submit it as `undefined` so the API stores null, not ''.
export const optionalText = () => z
  .string()
  .transform(value => value || undefined)
  .optional() as unknown as z.ZodOptional<z.ZodType<string | undefined, string | undefined>>;

// The rich text editor returns HTML, and an emptied editor still holds markup such as `<p></p>`:
// require visible text, not just a non-empty string.
export const requiredRichText = (t: Translate) => z
  .string()
  .refine(
    value => (new DOMParser().parseFromString(value, 'text/html').body.textContent ?? '').trim().length > 0,
    { error: t('This field is required.') },
  );

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
