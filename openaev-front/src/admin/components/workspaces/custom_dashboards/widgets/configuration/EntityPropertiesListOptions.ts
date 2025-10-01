import { type PropertySchemaDTO } from '../../../../../../utils/api-types';
import { type Widget } from '../../../../../../utils/api-types-custom';
import { type GroupOption } from '../../../../../../utils/Option';
import { getAvailableFields } from '../WidgetUtils';

const getEntityPropertiesListOptions = (props: PropertySchemaDTO[], widgetType: Widget['widget_type'], preFilter: ((prop: PropertySchemaDTO) => boolean) | undefined = undefined): GroupOption[] => {
  const filteredProps = preFilter ? props.filter(preFilter) : props;
  const newOptions: GroupOption[] = filteredProps
    .reduce<GroupOption[]>((acc, d) => {
      let group = 'Specific properties';
      if (d.schema_property_name.includes('_side')) {
        group = 'Relationship properties';
      } else if (d.schema_property_name.includes('base_')) {
        group = 'Common properties';
      }
      acc.push({
        id: d.schema_property_name,
        label: d.schema_property_name,
        group,
      });
      return acc;
    }, [])
    .sort((a, b) => {
      if (a.group < b.group) return -1;
      if (a.group > b.group) return 1;
      return a.label.localeCompare(b.label);
    });
  const availableFields = getAvailableFields(widgetType);
  return !availableFields ? newOptions : newOptions.filter(o => availableFields.includes(o.id));
};

export default getEntityPropertiesListOptions;
