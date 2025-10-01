import {
  MenuItem,
  TextField,
} from '@mui/material';
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

const ENTITIES = ['expectation-inject', 'finding', 'endpoint', 'vulnerable-endpoint', 'inject', 'scenario', 'simulation'];

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
    <TextField
      select
      variant="standard"
      fullWidth
      label={t('Entity type')}
      value={value}
      error={error}
      helperText={error ? t('Should at least select one dimension') : ''}
      onChange={e => onChange(e.target.value)}
      required
    >
      {entityOptions.map(option => (
        <MenuItem key={option.id} value={option.id}>
          {t(option.label)}
        </MenuItem>
      ))}
    </TextField>
  );
};

export default FilterFieldBaseEntity;
