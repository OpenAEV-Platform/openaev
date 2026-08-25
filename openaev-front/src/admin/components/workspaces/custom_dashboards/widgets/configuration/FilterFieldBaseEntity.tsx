import {
  Select,
  SelectContent,
  SelectHelperText,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { engineSchemas } from '../../../../../../actions/schema/schema-action';
import { useFormatter } from '../../../../../../components/i18n';
import Loader from '../../../../../../components/Loader';
import { type PropertySchemaDTO } from '../../../../../../utils/api-types';
import { type Option } from '../../../../../../utils/Option';

interface Props {
  value: string | null;
  onChange: (value: string | null) => void;
  error?: boolean;
}

const ENTITIES = ['expectation-inject', 'finding', 'asset', 'vulnerable-endpoint', 'inject', 'scenario', 'simulation'];

const FilterFieldBaseEntity: FunctionComponent<Props> = ({
  value,
  onChange,
  error = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [loading, setLoading] = useState<boolean>(true);
  const [entityOptions, setEntityOptions] = useState<Option[]>([]);

  useEffect(() => {
    engineSchemas().then((response: { data: PropertySchemaDTO[] }) => {
      const entities = Array.from(new Set(
        response.data.map(d => d.schema_property_entity),
      )).filter(e => ENTITIES.includes(e));
      setEntityOptions(entities.map(entity => ({
        id: entity,
        label: t(entity),
      })));
      setLoading(false);
    });
  }, []);

  if (loading) {
    return <Loader variant="inElement" />;
  }

  return (
    <Select
      value={value ?? ''}
      onValueChange={next => onChange(next)}
      error={error}
      required={true}
    >
      <SelectLabel required>{t('Entity type')}</SelectLabel>
      <SelectTrigger>
        <SelectValue placeholder={t('Entity type')} />
      </SelectTrigger>
      <SelectContent>
        {entityOptions.map(option => (
          <SelectItem key={option.id} value={option.id}>
            {t(option.label)}
          </SelectItem>
        ))}
      </SelectContent>
      <SelectHelperText>{error ? t('Should at least select one dimension') : ''}</SelectHelperText>
    </Select>
  );
};

export default FilterFieldBaseEntity;
