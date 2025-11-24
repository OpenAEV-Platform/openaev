import { searchAtomicTestingTeams } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import { type Page } from '../../../../../components/common/queryable/Page';
import { type SearchPaginationInput, type TeamWithTagsOutput } from '../../../../../utils/api-types';
import { type TeamContextType } from '../../../common/Context';

const teamContextForAtomicTesting = (): TeamContextType => {
  return {
    searchTeams(input: SearchPaginationInput, contextualOnly?: boolean): Promise<{ data: Page<TeamWithTagsOutput> }> {
      return searchAtomicTestingTeams(input, contextualOnly);
    },
  };
};

export default teamContextForAtomicTesting;
