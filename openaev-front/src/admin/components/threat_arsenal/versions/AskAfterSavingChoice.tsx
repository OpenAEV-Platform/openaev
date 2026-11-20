import { Alert, Button } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useSkipNewVersionDialog from './useSkipNewVersionDialog';

/** Turns back on the New version dialog the user switched off with "Don't ask me again". */
const AskAfterSavingChoice: FunctionComponent = () => {
  const { t } = useFormatter();
  const [skipsDialog, setSkipsDialog] = useSkipNewVersionDialog();
  if (!skipsDialog) {
    return null;
  }
  return (
    <Alert
      severity="info"
      title={t('After saving a new version, you are not asked to move injects.')}
      action={<Button priority="secondary" size="sm" onClick={() => setSkipsDialog(false)}>{t('Ask me again')}</Button>}
    />
  );
};

export default AskAfterSavingChoice;
