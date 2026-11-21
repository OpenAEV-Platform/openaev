import { useTheme } from '@mui/material/styles';

import CardGrid from '../../../../components/common/card/CardGrid';
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
    <>
      <CardGrid loading={loading} style={{ marginTop: theme.spacing(1) }}>
        {findings.map(finding => <FindingCard key={finding.finding_id} finding={finding} />)}
      </CardGrid>
      {!loading && findings.length === 0 && <Empty message={t('No finding found.')} />}
    </>
  );
};

export default FindingCardView;
