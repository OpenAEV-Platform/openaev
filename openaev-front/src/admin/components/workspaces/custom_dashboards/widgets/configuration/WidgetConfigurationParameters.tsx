import { TextField } from '@mui/material';
import { type FunctionComponent } from 'react';
import { type Control, Controller, type UseFormSetValue } from 'react-hook-form';

import { useFormatter } from '../../../../../../components/i18n';
import { type Widget } from '../../../../../../utils/api-types';
import { type WidgetInputWithoutLayout } from '../WidgetUtils';
import WidgetSecurityDomainsParameters from './domains/WidgetSecurityDomainsParameters';
import HistogramParameters from './histogram/HistogramParameters';
import ListWidgetParameters from './list/ListWidgetParameters';
import NumberWidgetParameters from './number/NumberWidgetParameters';

const WidgetConfigurationParameters: FunctionComponent<{
  widgetType: Widget['widget_type'];
  control: Control<WidgetInputWithoutLayout>;
  setValue: UseFormSetValue<WidgetInputWithoutLayout>;
}> = ({ widgetType, control, setValue }) => {
  // Standard hooks
  const { t } = useFormatter();
  const getParametersControl = (widgetType: Widget['widget_type']) => {
    switch (widgetType) {
      case 'list':
        return <ListWidgetParameters setValue={setValue} control={control} widgetType={widgetType} />;
      case 'number':
        return <NumberWidgetParameters widgetType={widgetType} control={control} setValue={setValue} />;
      case 'average':
        return <WidgetSecurityDomainsParameters widgetType={widgetType} control={control} setValue={setValue} />;
      default:
        return <HistogramParameters setValue={setValue} control={control} widgetType={widgetType} />;
    }
  };

  return (
    <>
      <Controller
        control={control}
        name="widget_config.title"
        render={({ field }) => (
          <TextField
            {...field}
            variant="standard"
            fullWidth
            label={t('Title')}
            value={field.value ?? ''}
            onChange={e => field.onChange(e.target.value)}
          />
        )}
      />
      {getParametersControl(widgetType)}
    </>
  );
};

export default WidgetConfigurationParameters;
