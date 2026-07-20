import { type FunctionComponent } from 'react';
import { useParams } from 'react-router';

import { searchDistinctFindingsForInjects } from '../../../../actions/findings/finding-actions';
import { type InjectResultOverviewOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import FindingList from '../../findings/FindingList';

const AtomicTestingFindings: FunctionComponent = () => {
  const { injectId } = useParams() as { injectId: InjectResultOverviewOutput['inject_id'] };

  const searchDistinct = (input: SearchPaginationInput) => {
    return searchDistinctFindingsForInjects(injectId, input);
  };

  return (
    <FindingList filterLocalStorageKey="atm-findings" searchDistinctFindings={searchDistinct} contextId={injectId} />
  );
};

export default AtomicTestingFindings;
