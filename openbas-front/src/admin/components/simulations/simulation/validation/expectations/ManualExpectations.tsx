import { AssignmentTurnedIn, ExpandMore, PersonOutlined } from '@mui/icons-material';
import { Accordion, AccordionDetails, AccordionSummary, Alert, AlertTitle, Chip, Divider, List, ListItemButton, ListItemIcon, ListItemText, Tooltip, Typography } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, type SyntheticEvent, useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type UserHelper } from '../../../../../../actions/helper';
import { fetchPlayers } from '../../../../../../actions/User';
import colorStyles from '../../../../../../components/Color';
import Drawer from '../../../../../../components/common/Drawer';
import ExpandableText from '../../../../../../components/common/ExpendableText';
import Paper from '../../../../../../components/common/Paper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type Inject, type User } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { computeColorStyle } from '../../../../../../utils/statusColors';
import { computeLabel, resolveUserName, truncate } from '../../../../../../utils/String';
import { PermissionsContext } from '../../../../common/Context';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import { FAILED } from '../../../../common/injects/expectations/ExpectationUtils';
import ManualExpectationsValidationForm from './ManualExpectationsValidationForm';

const useStyles = makeStyles()(theme => ({
  item: { height: 40 },
  container: {
    display: 'flex',
    placeContent: 'space-between',
    fontSize: theme.typography.h3.fontSize,
  },
  chip: {
    display: 'flex',
    gap: theme.spacing(3),
  },
  chipInList: {
    height: 20,
    borderRadius: 4,
    textTransform: 'uppercase',
    width: 200,
  },
  chipStatusAcc: {
    height: 30,
    borderRadius: 4,
    textTransform: 'uppercase',
    width: 150,
    float: 'right',
    marginLeft: 5,
  },
  points: {
    height: 20,
    backgroundColor: 'rgba(236, 64, 122, 0.08)',
    border: '1px solid #ec407a',
    color: '#ec407a',
  },
  validationType: {
    height: 20,
    border: '1px solid',
    borderRadius: 4,
  },
}));

interface Props {
  inject: Inject;
  expectations: InjectExpectationsStore[];
}

const ManualExpectations: FunctionComponent<Props> = ({
  inject,
  expectations,
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  const [selectedItem, setSelectedItem] = useState<string | null>(null);
  const [currentExpectations, setCurrentExpectations] = useState<InjectExpectationsStore[] | null>(null);
  const [expanded, setExpanded] = useState<string | false>(false);

  const { usersMap }: { usersMap: Record<string, User> } = useHelper((helper: UserHelper) => {
    return ({ usersMap: helper.getUsersMap() });
  });
  const dispatch = useAppDispatch();

  useDataLoader(() => {
    dispatch(fetchPlayers());
  });

  const handleItemClick = (expectationsToUpdate: InjectExpectationsStore[]) => {
    setSelectedItem(expectationsToUpdate[0]?.inject_expectation_name || null);
    setCurrentExpectations(expectationsToUpdate);
  };
  const handleItemClose = () => {
    setSelectedItem(null);
    setCurrentExpectations(null);
  };

  const handleChange = (panel: string) => (_event: SyntheticEvent, isExpanded: boolean) => {
    setExpanded(isExpanded ? panel : false);
  };

  const parentExpectation = expectations.filter(e => !e.inject_expectation_user)[0];
  const childrenExpectations = expectations.filter(e => e.inject_expectation_user);
  const validatedCount = expectations.filter(v => !R.isEmpty(v.inject_expectation_results)).length;
  const isAllValidated = validatedCount === expectations.length;

  let label;
  let style;
  if (!isAllValidated || !parentExpectation) {
    label = t('Pending validation');
    style = colorStyles.orange;
  } else {
    const results = parentExpectation.inject_expectation_results ?? [];
    const hasFailed = results.some(result => result?.result === FAILED);

    if (hasFailed) {
      label = `${t('Failed')} (${parentExpectation.inject_expectation_score})`;
      style = colorStyles.red;
    } else {
      label = `${t('Success')} (${parentExpectation.inject_expectation_score})`;
      style = colorStyles.green;
    }
  }

  const targetLabel = (expectationToProcess: InjectExpectationsStore) => {
    if (expectationToProcess.inject_expectation_user && usersMap[expectationToProcess.inject_expectation_user]) {
      return truncate(resolveUserName(usersMap[expectationToProcess.inject_expectation_user]), 22);
    }
    return t('Unknown');
  };

  return (
    <>
      <List component="div" disablePadding>
        {expectations.length > 0 && (
          <ListItemButton
            key={expectations[0].inject_expectation_name}
            divider
            sx={{ pl: 8 }}
            classes={{ root: classes.item }}
            onClick={() => handleItemClick(expectations)}
            selected={selectedItem === expectations[0].inject_expectation_name}
          >
            <ListItemIcon>
              <AssignmentTurnedIn fontSize="small" />
            </ListItemIcon>
            <ListItemText
              primary={(
                <div className={classes.container}>
                  <Tooltip title={expectations[0].inject_expectation_description}>
                    <span>
                      {expectations[0].inject_expectation_name ?? 'Manual Expectation'}
                    </span>
                  </Tooltip>
                  <div className={classes.chip}>
                    <Chip
                      classes={{ root: classes.validationType }}
                      label={expectations[0].inject_expectation_group ? 'At least one player' : 'All players'}
                    />
                    <Chip
                      classes={{ root: classes.chipInList }}
                      style={style}
                      label={label}
                    />
                  </div>
                </div>
              )}
            />
          </ListItemButton>
        )}
      </List>
      <Drawer
        open={currentExpectations !== null}
        handleClose={() => handleItemClose()}
        title={t('Expectations of ') + inject.inject_title}
      >
        <>
          <Alert
            severity="warning"
            variant="outlined"
            style={{
              position: 'relative',
              marginBottom: 20,
            }}
          >
            <AlertTitle>
              <ExpandableText
                source={
                  expectations[0].inject_expectation_group
                    ? t('At least one player (per team) must validate the expectation')
                    : t('All players (per team) must validate the expectation')
                }
                limit={120}
              />
            </AlertTitle>
          </Alert>
          <Alert
            severity="info"
            variant="outlined"
            style={{
              position: 'relative',
              marginBottom: 20,
            }}
          >
            <AlertTitle>
              {t('The score set for the team will also be applied to all players in the team')}
            </AlertTitle>
          </Alert>
          <Typography
            variant="h5"
            style={{
              fontWeight: 500,
              margin: '10px',
            }}
          >
            {t('Team')}
          </Typography>
          <Paper>
            <ManualExpectationsValidationForm key={parentExpectation.target_id} expectation={parentExpectation} isDisabled={permissions.readOnly} />
          </Paper>
          <Divider style={{ margin: '20px 0' }} />
          <Typography
            variant="h5"
            style={{
              fontWeight: 500,
              margin: '10px',
            }}
          >
            {t('Players')}
          </Typography>
          <div style={{
            maxHeight: '80vh',
            overflowY: 'auto',
          }}
          >
            {childrenExpectations.map((e) => {
              const panelId = `panel-${e.inject_expectation_id}`;

              return (
                <Accordion
                  key={e.inject_expectation_id}
                  expanded={expanded === panelId}
                  onChange={handleChange(panelId)}
                  style={{
                    boxShadow: 'none',
                    margin: 0,
                  }}
                >
                  <AccordionSummary
                    expandIcon={<ExpandMore />}
                    aria-controls={`${panelId}-content`}
                    id={`${panelId}-header`}
                    style={{
                      boxShadow: 'none',
                      border: 'none',
                      height: '10px',
                    }}
                  >
                    <div style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      width: '100%',
                    }}
                    >
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                      }}
                      >
                        <PersonOutlined color="primary" />
                        <Typography style={{ marginLeft: 8 }}>{targetLabel(e)}</Typography>
                      </div>
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                      }}
                      >
                        <Chip label={e.inject_expectation_score ?? 0} style={{ marginRight: 8 }} />
                        <Chip
                          classes={{ root: classes.chipStatusAcc }}
                          style={computeColorStyle(e.inject_expectation_status)}
                          label={t(computeLabel(e.inject_expectation_status))}
                        />
                      </div>
                    </div>
                  </AccordionSummary>
                  <AccordionDetails>
                    <ManualExpectationsValidationForm expectation={e} withSummary={false} isDisabled={permissions.readOnly} />
                  </AccordionDetails>
                </Accordion>
              );
            })}
          </div>
        </>
      </Drawer>
    </>
  );
};

export default ManualExpectations;
