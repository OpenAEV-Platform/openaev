import { Icon } from '@filigran/design-system';
import { memo } from 'react';

import RightMenu, { type RightMenuEntry } from '../../../components/common/menu/RightMenu';

const DataIngestionMenuComponent = () => {
  const entries: RightMenuEntry[] = [
    {
      path: '/admin/settings/data_ingestion/xls_mappers',
      icon: () => (<Icon name="table-2" size={16} />),
      label: 'XLS mappers',
    },
  ];
  return (
    <RightMenu entries={entries} />
  );
};

const DataIngestionMenu = memo(DataIngestionMenuComponent);

export default DataIngestionMenu;
