import { Autocomplete, Chip, TextField } from '@mui/material';
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
    <Autocomplete
      multiple
      options={notifiers}
      value={selected}
      onChange={(_, newValue) => onChange(newValue.map(notifier => notifier.notifier_id))}
      getOptionLabel={notifier => notifier.notifier_name ?? ''}
      isOptionEqualToValue={(option, val) => option.notifier_id === val.notifier_id}
      style={style}
      renderTags={(tagValue, getTagProps) =>
        tagValue.map((option, index) => (
          <Chip
            {...getTagProps({ index })}
            key={option.notifier_id}
            label={option.notifier_name}
            size="small"
          />
        ))}
      renderInput={params => (
        <TextField
          {...params}
          label={t('Notifiers')}
          variant="standard"
          error={!!error}
          helperText={error}
        />
      )}
    />
  );
};

export default NotifierField;
