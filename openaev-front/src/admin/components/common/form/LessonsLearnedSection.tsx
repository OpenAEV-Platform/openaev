import { FormControlLabel, Switch, Typography } from '@mui/material';
import { type CSSProperties } from 'react';
import { type Control, Controller, type FieldValues, type Path } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';

interface Props<TFieldValues extends FieldValues> {
  control: Control<TFieldValues>;
  name: Path<TFieldValues>;
  disabled?: boolean;
  style?: CSSProperties;
}

const LessonsLearnedSection = <TFieldValues extends FieldValues>({
  control,
  name,
  disabled = false,
  style,
}: Props<TFieldValues>) => {
  const { t } = useFormatter();

  return (
    <div style={style}>
      <Typography
        variant="h2"
        gutterBottom
      >
        {t('Modules')}
      </Typography>
      <Controller
        control={control}
        name={name}
        render={({ field }) => (
          <FormControlLabel
            control={(
              <Switch
                checked={field.value ?? false}
                onChange={event => field.onChange(event.target.checked)}
                disabled={disabled}
              />
            )}
            label={t('Enable lessons learned')}
          />
        )}
      />
      <Typography variant="body2" color="textSecondary">
        {t('Adds a lessons learned tab to collect feedback with objectives and questionnaires.')}
      </Typography>
    </div>
  );
};

export default LessonsLearnedSection;
