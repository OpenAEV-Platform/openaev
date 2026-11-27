import { useTheme } from '@mui/material/styles';

import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import { type AggregatedFindingOutput } from '../../../../utils/api-types';
import FindingCard from './FindingCard';

interface Props {
  loading: boolean;
  findings: AggregatedFindingOutput[];
}

const FindingCardView = ({ loading, findings }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <div style={{ marginTop: theme.spacing(1) }}>
      <MarketplaceSections
        items={findings}
        loading={loading}
        emptyState={<Empty message={t('No finding found.')} />}
        renderItem={finding => <FindingCard key={finding.finding_id} finding={finding} />}
      />
    </div>
  );
};

export default FindingCardView;
