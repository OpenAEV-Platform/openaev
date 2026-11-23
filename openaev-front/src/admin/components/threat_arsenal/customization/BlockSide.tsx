import { Chip, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { type ConflictSide } from '../../../../components/common/update/ConflictChoice';
import TextDiff from '../../../../components/common/update/TextDiff';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadFieldUpdateOutput } from '../../../../utils/api-types';
import { CUSTOMIZABLE_FIELD_LABELS, type CustomizableField, fieldText } from './fieldUpdates';

const VALUE_OF: Record<ConflictSide, (field: PayloadFieldUpdateOutput) => unknown> = {
  theirs: field => field.field_filigran,
  mine: field => field.field_yours,
};

const OTHER_SIDE: Record<ConflictSide, ConflictSide> = {
  theirs: 'mine',
  mine: 'theirs',
};

interface ValueProps {
  text: string;
  other: string;
}

const Chips = ({ text }: ValueProps) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'flex',
      flexWrap: 'wrap',
      gap: theme.spacing(0.5),
    }}
    >
      {text.split('\n').map(item => <Chip key={item} label={item} />)}
    </div>
  );
};

const Lines = ({ text, other }: ValueProps) => <TextDiff text={text} other={other} />;

const VIEW_OF: Partial<Record<CustomizableField, FunctionComponent<ValueProps>>> = { PLATFORMS: Chips };

interface Props {
  fields: PayloadFieldUpdateOutput[];
  side: ConflictSide;
}

// One side of a conflicting block: platforms as chips, the other fields one item per line, the words that differ marked.
const BlockSide = ({ fields, side }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
    }}
    >
      {fields.map((field) => {
        const text = fieldText(field.field_name, VALUE_OF[side](field));
        const View = VIEW_OF[field.field_name] ?? Lines;
        return (
          <div key={field.field_name}>
            {fields.length > 1 && <Text variant="content-caption">{t(CUSTOMIZABLE_FIELD_LABELS[field.field_name])}</Text>}
            {text
              ? <View text={text} other={fieldText(field.field_name, VALUE_OF[OTHER_SIDE[side]](field))} />
              : <Text variant="content-caption">{t('Empty')}</Text>}
          </div>
        );
      })}
    </div>
  );
};

export default BlockSide;
