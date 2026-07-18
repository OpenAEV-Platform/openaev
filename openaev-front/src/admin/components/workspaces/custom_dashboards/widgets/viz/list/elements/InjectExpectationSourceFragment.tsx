import { Tooltip } from '@mui/material';

import { useFormatter } from '../../../../../../../../components/i18n';
import { type EsBase, type EsInjectExpectation } from '../../../../../../../../utils/api-types';
import getTargetTypeFromInjectExpectation from './injectExpectationTarget';

/**
 * Renders the target kind of an inject expectation. Expectations without a
 * resolvable target (no user/team/asset side) render a dash instead of
 * crashing formatjs with an empty translation id.
 */
const InjectExpectationSourceFragment = ({ element }: { element: EsBase }) => {
  const { t } = useFormatter();
  const target = getTargetTypeFromInjectExpectation(element as EsInjectExpectation);
  if (!target.label) {
    return <span>-</span>;
  }
  const label = t(target.label);
  return (
    <Tooltip title={label} placement="bottom-start">
      <span>{label.toUpperCase()}</span>
    </Tooltip>
  );
};

export default InjectExpectationSourceFragment;
