import { Autocomplete, TextField } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { engineSchemas } from '../../../../../../../actions/schema/schema-action';
import { useFormatter } from '../../../../../../../components/i18n';
import {
  type FilterGroup,
  type ListPerspective,
  type PropertySchemaDTO,
  type Series,
} from '../../../../../../../utils/api-types';
import type { Widget } from '../../../../../../../utils/api-types-custom';
import type { GroupOption } from '../../../../../../../utils/Option';
import { getBaseEntities } from '../../WidgetUtils';
import getEntityPropertiesListOptions from '../EntityPropertiesListOptions';

type Props = {
  widgetType: Widget['widget_type'];
  series: Series[] | ListPerspective[];
};

const WidgetConfigDateAttributeController: FunctionComponent<Props> = ({ widgetType, series }) => {
  const { t } = useFormatter();
  const { control } = useFormContext();

  const entities = (series ?? []).map((v: { filter?: FilterGroup }) => getBaseEntities(v.filter)).flat();

  const [dateOptions, setDateOptions] = useState<GroupOption[]>([]);
  // get the entity schema for column date attribute
  useEffect(() => {
    engineSchemas(entities).then((response: { data: PropertySchemaDTO[] }) => {
      const finalOptions = getEntityPropertiesListOptions(
        response.data,
        widgetType,
        d => d.schema_property_type === 'instant')
        .map((o) => {
          return {
            ...o,
            label: t(o.label),
          };
        });
      setDateOptions(finalOptions);
    });
  }, []);

  return (
    <Controller
      control={control}
      name="widget_config.date_attribute"
      render={({ field, fieldState }) => {
        return (
          <Autocomplete
            options={dateOptions}
            groupBy={option => option.group}
            value={dateOptions.find(o => o.id === field.value) ?? null}
            onChange={(_, value) => field.onChange(value?.id)}
            getOptionLabel={option => option.label ?? ''}
            isOptionEqualToValue={(option, value) => option.id === value.id}
            renderInput={params => (
              <TextField
                {...params}
                label={t('Date attribute')}
                variant="standard"
                fullWidth
                sx={{ mt: 2 }}
                error={!!fieldState.error}
                helperText={fieldState.error?.message}
                required
              />
            )}
            freeSolo={false}
          />
        );
      }}
    />
  );
};

export default WidgetConfigDateAttributeController;
