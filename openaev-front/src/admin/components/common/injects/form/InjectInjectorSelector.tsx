import { useMemo } from 'react';

import SelectFieldController from '../../../../../components/fields/SelectFieldController';
import { useFormatter } from '../../../../../components/i18n';

interface Props {
  injectorNames: Record<string, string>;
  disabled?: boolean;
}

const InjectInjectorSelector = ({ injectorNames, disabled = false }: Props) => {
  const { t } = useFormatter();

  const injectorIds = Object.keys(injectorNames);
  const showInjectorSelector = injectorIds.length > 1;

  const injectorItems = useMemo(() => {
    if (!showInjectorSelector) return [];
    return injectorIds.map(id => ({
      value: id,
      label: injectorNames[id] ?? id,
    }));
  }, [showInjectorSelector, injectorIds, injectorNames]);

  if (!showInjectorSelector) return null;

  return (
    <SelectFieldController
      name="inject_injector"
      label={t('Injector')}
      required={true}
      items={injectorItems}
      disabled={disabled}
    />
  );
};

export default InjectInjectorSelector;
