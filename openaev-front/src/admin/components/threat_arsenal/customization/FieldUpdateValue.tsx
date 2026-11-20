import { Chip, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { fieldText } from './fieldUpdates';
import sameValue from './sameValue';

interface Props { value: unknown }

// A field value as text: platforms as chips, arguments and prerequisites one per line.
const FieldUpdateValue: FunctionComponent<Props> = ({ value }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  if (sameValue(value, null)) {
    return <Text variant="content-caption" className="text-default-secondary">{t('Empty')}</Text>;
  }
  if (Array.isArray(value) && value.every(item => typeof item === 'string')) {
    return (
      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: theme.spacing(0.5),
      }}
      >
        {(value as string[]).map(item => <Chip key={item} label={item} />)}
      </div>
    );
  }
  return (
    <Text
      variant="content-code"
      style={{
        whiteSpace: 'pre-wrap',
        overflowWrap: 'anywhere',
      }}
    >
      {fieldText(value)}
    </Text>
  );
};

export default FieldUpdateValue;
