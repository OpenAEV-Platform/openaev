import { Button } from '@filigran/design-system';
import { ExploreOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import EmptyPlaceholder from '../../../components/common/EmptyPlaceholder';
import { useFormatter } from '../../../components/i18n';

interface Props {
  hasFilters: boolean;
  onResetFilters: () => void;
  // What an empty list without filters says, when it is not the Active tab.
  empty?: {
    title: string;
    description: string;
  };
}

const ThreatArsenalEmptyState: FunctionComponent<Props> = ({ hasFilters, onResetFilters, empty }) => {
  const { t } = useFormatter();
  if (hasFilters) {
    return (
      <EmptyPlaceholder
        icon={<ExploreOutlined />}
        title={t('No actions match your filters')}
        message={t('Try adjusting filters or clearing them to see more actions.')}
        action={<Button type="button" priority="secondary" onClick={onResetFilters}>{t('Reset filters')}</Button>}
      />
    );
  }
  return (
    <EmptyPlaceholder
      icon={<ExploreOutlined />}
      title={empty?.title ?? t('Your threat arsenal is empty')}
      message={empty?.description ?? t('Create your first action or import an existing arsenal to get started.')}
    />
  );
};

export default ThreatArsenalEmptyState;
