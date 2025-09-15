import { List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import { AbilityContext } from '../../../../../utils/permissions/PermissionsProvider';
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
  predefinedExpectationDatas: ExpectationInput[];
  expectationDatas: ExpectationInput[];
  handleExpectations: (expectations: ExpectationInput[]) => void;
  readOnly?: boolean;
  injectId?: string;
}

const InjectExpectations: FunctionComponent<InjectExpectationsProps> = ({
  predefinedExpectationDatas = [],
  expectationDatas,
  handleExpectations,
  injectId,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { permissions, inherited_context } = useContext(PermissionsContext);
  const ability = useContext(AbilityContext);
  const userCanAddExpectations = permissions.canManage || (inherited_context == INHERITED_CONTEXT.NONE && ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectId));

  const [sortedExpectations, setSortedExpectations] = useState<ExpectationInput[]>([]);
  const [sortBy] = useState('expectation_name');
  const [sortAsc] = useState(true);

  // Filter predefinedExpectations already included into expectations
  const predefinedExpectations = useMemo(() => predefinedExpectationDatas
    .filter(pe => !sortedExpectations.map(e => e.expectation_type).includes(pe.expectation_type)), [sortedExpectations]);

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
    const values = sortedExpectations.map((item, i) => (i !== idx ? item : expectation));
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
            secondaryAction={(
              <ExpectationPopover
                index={idx}
                expectation={expectation}
                handleUpdate={handleUpdateExpectation}
                handleDelete={handleRemoveExpectation}
              />
            )}
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
      { userCanAddExpectations
        && (
          <InjectAddExpectation
            handleAddExpectation={handleAddExpectation}
            predefinedExpectations={predefinedExpectations}
          />
        )}
    </>
  );
};

export default InjectExpectations;
