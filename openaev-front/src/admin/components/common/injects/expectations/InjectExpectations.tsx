import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../../utils/permissions/types';
import { truncate } from '../../../../../utils/String';
import { PermissionsContext } from '../../Context';
import { type ExpectationInput } from './Expectation';
import ExpectationPopover from './ExpectationPopover';
import { isAutomatic, typeIcon } from './ExpectationUtils';
import InjectAddExpectation from './InjectAddExpectation';

const useStyles = makeStyles()(theme => ({
  column: {
    display: 'grid',
    gridTemplateColumns: '2fr 1fr 1fr 1fr',
  },
  bodyItem: { fontSize: theme.typography.h3.fontSize },
}));

interface InjectExpectationsProps {
  expectationDatas: ExpectationInput[];
  handleExpectations: (expectations: ExpectationInput[]) => void;
  readOnly?: boolean;
  injectId?: string;
  predefinedExpectations?: ExpectationInput[];
  availableExpectations?: ExpectationInput[];
}

const InjectExpectations: FunctionComponent<InjectExpectationsProps> = ({
  expectationDatas,
  handleExpectations,
  readOnly = false,
  injectId,
  predefinedExpectations = [],
  availableExpectations = [],
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { permissions, inherited_context } = useContext(PermissionsContext);
  const ability = useContext(AbilityContext);
  const userCanAddExpectations = permissions.canManage || ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT)
    || (inherited_context === INHERITED_CONTEXT.NONE && ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId));

  const [sortedExpectations, setSortedExpectations] = useState<ExpectationInput[]>([]);
  const [sortBy] = useState<keyof ExpectationInput>('expectation_name');
  const [sortAsc] = useState(true);

  const expectationsAvailableInContract = availableExpectations.length > 0
    ? availableExpectations
    : predefinedExpectations;

  // Filter contract available expectations already included into current inject expectations.
  // Manual expectations can be added as many times as we want
  const addableAvailableExpectations = useMemo(() => expectationsAvailableInContract
    .filter(pe => !sortedExpectations.map(e => e.expectation_type).includes(pe.expectation_type) || pe.expectation_type === 'MANUAL'), [sortedExpectations, expectationsAvailableInContract]);

  const sortExpectations = R.sortWith(
    sortAsc
      ? [R.ascend(R.prop(sortBy))]
      : [R.descend(R.prop(sortBy))],
  );

  useEffect(() => {
    if (expectationDatas) {
      setSortedExpectations(sortExpectations(expectationDatas));
    }
  }, [expectationDatas]);

  // -- ACTIONS --

  const handleAddExpectation = (expectation: ExpectationInput) => {
    const values = [...sortedExpectations, expectation];
    setSortedExpectations(sortExpectations(values));
    handleExpectations(values);
  };

  const handleUpdateExpectation = (expectation: ExpectationInput, idx: number) => {
    const values = sortedExpectations.map((item, i) => (i === idx ? expectation : item));
    setSortedExpectations(sortExpectations(values));
    handleExpectations(values);
  };

  const handleRemoveExpectation = (idx: number) => {
    const values = sortedExpectations.filter((_, i) => i !== idx);
    setSortedExpectations(values);
    handleExpectations(values);
  };

  // -- UTILS --

  const typeLabel = (type: string) => {
    if (isAutomatic(type)) {
      return t('Automatic');
    }
    return t('Manual');
  };

  return (
    <>
      <List>
        {sortedExpectations.map((expectation, idx) => (
          <ListItem
            key={expectation.expectation_name}
            divider
            secondaryAction={(readOnly
              ? undefined
              : (
                  <ExpectationPopover
                    index={idx}
                    expectation={expectation}
                    injectId={injectId}
                    handleUpdate={handleUpdateExpectation}
                    handleDelete={handleRemoveExpectation}
                  />
                ))}
          >
            <ListItemIcon>
              {typeIcon(expectation.expectation_type)}
            </ListItemIcon>
            <ListItemText
              primary={(
                <div className={classes.column}>
                  <div className={classes.bodyItem}>
                    {truncate(expectation.expectation_name || '', 40)}
                  </div>
                  <div className={classes.bodyItem}>
                    {truncate(expectation.expectation_description || '', 15)}
                  </div>
                  <div className={classes.bodyItem}>
                    {expectation.expectation_score}
                  </div>
                  <div className={classes.bodyItem}>
                    {typeLabel(expectation.expectation_type)}
                  </div>
                </div>
              )}
            />
          </ListItem>
        ))}
      </List>
      { !readOnly && userCanAddExpectations && addableAvailableExpectations.length !== 0
        && (
          <InjectAddExpectation
            disabled={readOnly}
            handleAddExpectation={handleAddExpectation}
            predefinedExpectations={predefinedExpectations}
            availableExpectations={addableAvailableExpectations}
          />
        )}
    </>
  );
};

export default InjectExpectations;
