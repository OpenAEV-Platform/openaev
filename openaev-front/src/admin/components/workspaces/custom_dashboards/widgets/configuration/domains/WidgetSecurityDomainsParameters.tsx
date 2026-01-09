import { FunctionComponent, useEffect } from 'react';
import { WidgetInputWithoutLayout } from '../../WidgetUtils';
import { Control, Controller, UseFormSetValue, useWatch } from 'react-hook-form';
import type { Widget } from '../../../../../../../utils/api-types';
import WidgetConfigDateAttributeController from '../common/WidgetConfigDateAttributeController';
import WidgetConfigTimeRangeController from '../common/WidgetConfigTimeRangeController';

interface Props {
  widgetType: Widget['widget_type'];
  control: Control<WidgetInputWithoutLayout>;
  setValue: UseFormSetValue<WidgetInputWithoutLayout>;
}

const WidgetSecurityDomainsParameters: FunctionComponent<Props> = ({
  widgetType,
  control,
  setValue
}) => {

  useEffect(() => {
    setValue('widget_config.widget_configuration_type', 'average');
  }, []);

  const series = useWatch({
    control,
    name: 'widget_config.series',
  });


  return (
    <>
      <Controller
        control={control}
        name="widget_config.widget_configuration_type"
        render={({ field }) => (
          <input
            {...field}
            type="hidden"
            value={field.value ?? ''}
          />
        )}
      />
      <WidgetConfigDateAttributeController widgetType={widgetType} series={series} />
      <WidgetConfigTimeRangeController />
    </>
  );

};

export default WidgetSecurityDomainsParameters;
