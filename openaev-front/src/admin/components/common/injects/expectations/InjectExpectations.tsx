import {
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Typography,
} from '@mui/material';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../../components/ItemSecurityPlatformType';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, INHERITED_CONTEXT, SUBJECTS } from '../../../../../utils/permissions/types';
import { truncate } from '../../../../../utils/String';
import { PermissionsContext } from '../../Context';
import { type ExpectationInput } from './Expectation';
import ExpectationPopover from './ExpectationPopover';
import { isAutomatic, isTechnicalExpectation, typeIcon } from './ExpectationUtils';
import InjectAddExpectation from './InjectAddExpectation';

const useStyles = makeStyles()(theme => ({
  column: {
    display: 'grid',
    gridTemplateColumns: '2fr 1fr 1fr 1fr 1.5fr',
    alignItems: 'center',
    gap: theme.spacing(1),
  },
  bodyItem: { fontSize: theme.typography.h3.fontSize },
}));

interface InjectExpectationsProps {
  expectationDatas: ExpectationInput[];
  handleExpectations: (expectations: ExpectationInput[]) => void;
  readOnly?: boolean;
  injectId?: string;
  availableExpectations?: ExpectationInput[];
  inline?: boolean;
}

const InjectExpectations: FunctionComponent<InjectExpectationsProps> = ({
  expectationDatas,
  handleExpectations,
  readOnly = false,
  injectId,
  availableExpectations = [],
  inline = false,
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


  // Filter contract available expectations already included into current inject expectations.
  // expectation_is_multi_selectable=true means the type can be selected multiple times.
  const addableAvailableExpectations = useMemo(() => {
    const selectedTypes = new Set(sortedExpectations.map(e => e.expectation_type));
    return availableExpectations.filter((expectation) => {
      const isMultiSelectable = expectation.expectation_is_multi_selectable ?? false;
      return isMultiSelectable || !selectedTypes.has(expectation.expectation_type);
    });
  }, [sortedExpectations, availableExpectations]);

  const sortExpectations = (expectations: ExpectationInput[]): ExpectationInput[] =>
    [...expectations].sort((a, b) => {
      const valA = a[sortBy] ?? '';
      const valB = b[sortBy] ?? '';
      return sortAsc
        ? String(valA).localeCompare(String(valB))
        : String(valB).localeCompare(String(valA));
    });

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

  const canAddExpectation = !readOnly && userCanAddExpectations && addableAvailableExpectations.length !== 0;

  // -- UTILS --

  const typeLabel = (type: string) => {
    if (isAutomatic(type)) {
      return t('Automatic');
    }
    return t('Manual');
  };

  return (
    <>
      {inline && (
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
        >
          <Typography variant="subtitle2" fontWeight={600}>
            {t('Expectations')}
          </Typography>
          {canAddExpectation && (
            <InjectAddExpectation
              disabled={readOnly}
              handleAddExpectation={handleAddExpectation}
              availableExpectations={addableAvailableExpectations}
              inline
            />
          )}
        </div>
      )}
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
                  <div style={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 4,
                  }}
                  >
                    {(() => {
                      const expectedTypes = expectation.expectation_expected_security_platform_types ?? [];
                      if (!isTechnicalExpectation(expectation.expectation_type)) {
                        return null;
                      }
                      if (expectedTypes.length === 0) {
                        return (
                          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                            {t('Any security platform')}
                          </Typography>
                        );
                      }
                      return expectedTypes.map(type => (
                        <ItemSecurityPlatformType key={type} type={type} />
                      ));
                    })()}
                  </div>
                </div>
              )}
            />
          </ListItem>
        ))}
      </List>
      {!inline && canAddExpectation && (
        <InjectAddExpectation
          disabled={readOnly}
          handleAddExpectation={handleAddExpectation}
          availableExpectations={addableAvailableExpectations}
        />
      )}
    </>
  );
};

export default InjectExpectations;
