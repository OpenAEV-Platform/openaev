import { Button } from '@filigran/design-system';
import { SearchOffOutlined } from '@mui/icons-material';

import EmptyPlaceholder from '../../../../components/common/EmptyPlaceholder';
import { useFormatter } from '../../../../components/i18n';

interface Props { onResetFilters: () => void }

const CatalogEmptyState = ({ onResetFilters }: Props) => {
  const { t } = useFormatter();
  return (
    <EmptyPlaceholder
      icon={<SearchOffOutlined />}
      title={t('No connectors match your filters')}
      message={t('Try adjusting your search or clearing some filters.')}
      action={<Button type="button" priority="secondary" size="sm" onClick={onResetFilters}>{t('Reset filters')}</Button>}
    />
  );
};

export default CatalogEmptyState;
