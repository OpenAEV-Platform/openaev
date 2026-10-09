import { Chip } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';

/** An approved action whose new version waits for approval (it keeps running the approved one). */
const PendingVersionChip: FunctionComponent = () => {
  const { t } = useFormatter();
  return (
    <Chip
      label={t('New version pending')}
      severity="medium"
      style={{ maxWidth: '100%' }}
    />
  );
};

export default PendingVersionChip;
