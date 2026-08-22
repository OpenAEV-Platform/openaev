import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { engineSchemas } from '../../../../../../../actions/schema/schema-action';
import { useFormatter } from '../../../../../../../components/i18n';
import {
  type FilterGroup,
  type ListPerspective,
  type PropertySchemaDTO,
  type Series,
  type Widget,
} from '../../../../../../../utils/api-types';
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
          <Box sx={{ mt: 2 }}>
            <Combobox
              options={dateOptions}
              groupBy={option => option.group}
              value={dateOptions.find(o => o.id === field.value) ?? null}
              onValueChange={value => field.onChange((value as { id: string } | null)?.id)}
              getOptionLabel={option => option.label ?? ''}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              required
              error={!!fieldState.error}
            >
              <ComboboxLabel>{t('Date attribute')}</ComboboxLabel>
              <ComboboxField>
                <ComboboxInput />
                <ComboboxControls>
                  <ComboboxTrigger />
                </ComboboxControls>
              </ComboboxField>
              <ComboboxContent />
              {fieldState.error?.message ? <ComboboxHelperText>{fieldState.error.message}</ComboboxHelperText> : null}
            </Combobox>
          </Box>
        );
      }}
    />
  );
};

export default WidgetConfigDateAttributeController;
