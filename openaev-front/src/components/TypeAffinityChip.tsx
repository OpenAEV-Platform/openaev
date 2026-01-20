import { Chip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { FunctionComponent } from 'react';

interface Props { affinity_text?: string | null }

const TypeAffinityChip: FunctionComponent<Props> = ({ affinity_text }) => {
  const theme = useTheme();

  if (!affinity_text) return <>-</>;

  return (
    <Chip
      variant="outlined"
      label={affinity_text}
      style={{
        fontSize: 12,
        height: 25,
        margin: theme.spacing(0, 1, 1, 0),
        textTransform: 'uppercase',
        borderRadius: 4,
        width: 180,
      }}
    />
  );
};

export default TypeAffinityChip;
