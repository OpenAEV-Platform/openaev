import { Chip, type ChipSeverity, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../../components/i18n';

interface Count {
  label: string;
  count: number;
  severity?: ChipSeverity;
}

interface Props {
  title: string;
  counts: Count[];
  conflicts: number;
}

// The size of an update or a rollback: what happens counted, then whether anything waits for a choice.
const ScenarioUpdateSummary: FunctionComponent<Props> = ({ title, counts, conflicts }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const shown = counts.filter(count => count.count > 0);
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
    }}
    >
      <Text variant="title-xs">{title}</Text>
      {shown.length > 0 && (
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: theme.spacing(1),
        }}
        >
          {shown.map(count => <Chip key={count.label} label={count.label} severity={count.severity} />)}
        </div>
      )}
      <Text variant="content-caption">
        {conflicts > 0
          ? t('Choose only for the conflicts. Everything else is merged for you.')
          : t('Nothing to decide. Review the details or apply.')}
      </Text>
    </div>
  );
};

export default ScenarioUpdateSummary;
