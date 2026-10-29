import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent, useEffect, useState } from 'react';

import { fetchNotifiers } from '../../../../actions/notifications/notifier-actions';
import { useFormatter } from '../../../../components/i18n';
import { type NotifierOutput } from '../../../../utils/api-types';

interface Props {
  value: string[];
  onChange: (value: string[]) => void;
  error?: string;
  style?: CSSProperties;
}

/** Autocomplete multi-select over the tenant's notifiers (UI / email / webhook). */
const NotifierField: FunctionComponent<Props> = ({
  value,
  onChange,
  error,
  style,
}) => {
  const { t } = useFormatter();
  const [notifiers, setNotifiers] = useState<NotifierOutput[]>([]);

  useEffect(() => {
    fetchNotifiers().then((result: { data: NotifierOutput[] }) => setNotifiers(result.data ?? []));
  }, []);

  const selected = notifiers.filter(notifier => value.includes(notifier.notifier_id));

  return (
    <div style={style}>
      <Combobox<NotifierOutput>
        multiple
        options={notifiers}
        value={selected}
        onValueChange={newValue => onChange((newValue as NotifierOutput[]).map(notifier => notifier.notifier_id))}
        getOptionLabel={notifier => notifier.notifier_name ?? ''}
        isOptionEqualToValue={(option, val) => option.notifier_id === val.notifier_id}
        error={!!error}
      >
        <ComboboxLabel>{t('Notifiers')}</ComboboxLabel>
        <ComboboxField>
          <ComboboxChips />
          <ComboboxInput />
          <ComboboxControls>
            <ComboboxClear />
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent />
        {error ? <ComboboxHelperText>{error}</ComboboxHelperText> : null}
      </Combobox>
    </div>
  );
};

export default NotifierField;
