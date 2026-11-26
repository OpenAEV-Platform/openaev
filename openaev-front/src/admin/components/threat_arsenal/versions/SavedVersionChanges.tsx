import { Text } from '@filigran/design-system';
import { type FunctionComponent, useState } from 'react';

import FoldRow from '../../../../components/common/FoldRow';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import useVersionChanges from './useVersionChanges';
import VersionChanges from './VersionChanges';

interface Props {
  // The version the save made, and the one just before it.
  saved: PayloadVersionOutput;
  previous: PayloadVersionOutput;
}

// Right after a save: what it changed, open under its title, inside the note's frame.
const SavedVersionChanges: FunctionComponent<Props> = ({ saved, previous }) => {
  const { t } = useFormatter();
  const [open, setOpen] = useState(true);
  const changes = useVersionChanges(saved.payload_action_id);
  return (
    <>
      <FoldRow
        open={open}
        onToggle={() => setOpen(!open)}
        style={{
          padding: 0,
          border: 0,
        }}
      >
        <Text variant="content-base-bold">{t('What changed in v{version}', { version: saved.payload_version })}</Text>
      </FoldRow>
      {open && (
        <VersionChanges
          changes={changes}
          beforeVersion={previous.payload_version}
          afterVersion={saved.payload_version}
        />
      )}
    </>
  );
};

export default SavedVersionChanges;
