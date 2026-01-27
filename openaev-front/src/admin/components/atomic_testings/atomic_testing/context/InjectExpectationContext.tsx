import { createContext } from 'react';

import { type InjectExpectationResult } from '../../../../../utils/api-types';
import type { InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';

type InjectExpectationContextType = {
  onOpenDeleteInjectExpectationResult: (result: InjectExpectationResult | null, injectExpectationStore: InjectExpectationsStore | null) => void;
  onOpenEditInjectExpectationResultResult: (result: InjectExpectationResult | null, injectExpectationStore: InjectExpectationsStore | null) => void;
  onOpenSecurityPlatform: (result: InjectExpectationResult | null, injectExpectationStore: InjectExpectationsStore | null) => void;
};

const InjectExpectationContext = createContext<InjectExpectationContextType>({
  onOpenDeleteInjectExpectationResult: () => {},
  onOpenEditInjectExpectationResultResult: () => {},
  onOpenSecurityPlatform: () => {},
});

export default InjectExpectationContext;
