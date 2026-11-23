import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useSkipNewVersionDialog from './useSkipNewVersionDialog';
import VersionsTabNote from './VersionsTabNote';

/** Turns back on the New version dialog the user switched off with "Don't ask me again". */
const AskAfterSavingChoice: FunctionComponent = () => {
  const { t } = useFormatter();
  const [skipsDialog, setSkipsDialog] = useSkipNewVersionDialog();
  if (!skipsDialog) {
    return null;
  }
  return (
    <VersionsTabNote
      text={t('After saving a new version, you are not asked to move injects.')}
      actionLabel={t('Ask me again')}
      priority="tertiary"
      onAction={() => setSkipsDialog(false)}
    />
  );
};

export default AskAfterSavingChoice;
