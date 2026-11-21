import { Button, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import useSkipNewVersionDialog from './useSkipNewVersionDialog';

/** Turns back on the New version dialog the user switched off with "Don't ask me again". */
const AskAfterSavingChoice: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [skipsDialog, setSkipsDialog] = useSkipNewVersionDialog();
  if (!skipsDialog) {
    return null;
  }
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: theme.spacing(1),
    }}
    >
      <Text variant="content-compact" className="text-default-secondary">{t('After saving a new version, you are not asked to move injects.')}</Text>
      <Button priority="tertiary" size="sm" onClick={() => setSkipsDialog(false)}>{t('Ask me again')}</Button>
    </div>
  );
};

export default AskAfterSavingChoice;
