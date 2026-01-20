import { Chip } from '@mui/material';
import type { FunctionComponent } from 'react';

interface Props { affinity_text: string }

const TypeAffinityChip: FunctionComponent<Props> = ({ affinity_text }) => {
  return (
    <>
      { affinity_text
        && (
          <Chip
            variant="outlined"
            label={affinity_text}
            style={{
              fontSize: 12,
              height: 25,
              margin: '0 7px 7px 0',
              textTransform: 'uppercase',
              borderRadius: 4,
              width: 180,
            }}
          />
        )}
      { !affinity_text
        && '-' }
    </>
  );
};

export default TypeAffinityChip;
