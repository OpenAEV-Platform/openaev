import { type FunctionComponent } from 'react';
import { useParams } from 'react-router';

import { searchFindingsForInjects } from '../../../../actions/findings/finding-actions';
import { type InjectResultOverviewOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import FindingList from '../../findings/FindingList';

const AtomicTestingFindings: FunctionComponent = () => {
  const { injectId } = useParams() as { injectId: InjectResultOverviewOutput['inject_id'] };

  const searchInContext = (input: SearchPaginationInput) => {
    return searchFindingsForInjects(injectId, input);
  };

  return (
    <FindingList filterLocalStorageKey="atm-findings" searchFindings={searchInContext} contextId={injectId} />
  );
};

export default AtomicTestingFindings;
