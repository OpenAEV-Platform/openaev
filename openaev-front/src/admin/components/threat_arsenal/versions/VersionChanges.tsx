import { Skeleton, Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import ChangesTable from '../../../../components/common/update/ChangesTable';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionChangeOutput } from '../../../../utils/api-types';
import versionFieldChanges from './versionFieldChanges';

interface ChangesProps {
  // What the version changed, field by field; null while it loads.
  changes: PayloadVersionChangeOutput[] | null;
}

const useChangeRows = (changes: ChangesProps['changes']) => {
  const { t } = useFormatter();
  return changes && versionFieldChanges(changes).map(row => ({
    ...row,
    field: t(row.field),
  }));
};

// The fields a version changed, in one line: the Changes column of the history.
export const VersionChangedFields: FunctionComponent<ChangesProps> = ({ changes }) => {
  const { t } = useFormatter();
  const rows = useChangeRows(changes);
  if (!rows) return <Skeleton className="h-4" />;
  return <>{rows.length > 0 ? rows.map(row => row.field).join(', ') : t('Nothing that runs')}</>;
};

interface Props extends ChangesProps {
  beforeVersion: string;
  afterVersion: string;
}

// What one version changed from the one before it: the same before / after block as every other diff.
const VersionChanges: FunctionComponent<Props> = ({ changes, beforeVersion, afterVersion }) => {
  const { t } = useFormatter();
  const rows = useChangeRows(changes);
  if (!rows) {
    return <Skeleton className="h-16" />;
  }
  if (rows.length === 0) {
    return <Text variant="content-compact" className="text-default-secondary">{t('Nothing that runs changed in this version.')}</Text>;
  }
  return (
    <ChangesTable
      rows={rows}
      beforeLabel={t('Before · v{version}', { version: beforeVersion })}
      afterLabel={t('After · v{version}', { version: afterVersion })}
    />
  );
};

export default VersionChanges;
