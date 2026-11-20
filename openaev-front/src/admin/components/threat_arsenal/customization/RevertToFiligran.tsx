import { Button } from '@filigran/design-system';
import { useContext } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import { CUSTOMIZABLE_FORM_FIELDS, type CustomizableField } from './fieldUpdates';
import FiligranValuesContext from './FiligranValuesContext';
import sameValue from './sameValue';

interface Props { fields: CustomizableField[] }

// Shown on a customized copy whose fields differ from Filigran's.
const RevertToFiligran = ({ fields: customizable }: Props) => {
  const { t } = useFormatter();
  const fields = customizable.map(field => CUSTOMIZABLE_FORM_FIELDS[field]);
  const filigranValues = useContext(FiligranValuesContext);
  const { control, setValue } = useFormContext();
  const current = useWatch({
    control,
    name: fields,
  }) as unknown[];
  const filigran = filigranValues as Record<string, unknown> | null;
  if (!filigran || fields.every((field, index) => sameValue(current[index], filigran[field]))) {
    return null;
  }
  return (
    <Button
      type="button"
      size="sm"
      priority="tertiary"
      onClick={() => fields.forEach(field => setValue(field, filigran[field], {
        shouldDirty: true,
        shouldValidate: true,
      }))}
    >
      {t('Revert to Filigran\'s')}
    </Button>
  );
};

export default RevertToFiligran;
