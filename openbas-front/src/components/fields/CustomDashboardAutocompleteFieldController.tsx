import { Controller, type FieldValues, type Path, useFormContext } from 'react-hook-form';

import CustomDashboardAutocompleteField from './CustomDashboardAutocompleteField';

interface Props<T extends FieldValues> {
  name: Path<T>;
  label: string;
}

const CustomDashboardAutocompleteFieldController = <T extends FieldValues>({
  name,
  label,
}: Props<T>) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value } }) => (
        <CustomDashboardAutocompleteField label={label} value={value} onChange={onChange} />)}
    />
  );
};

export default CustomDashboardAutocompleteFieldController;
