import { Box, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Link, useLocation, useNavigate } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { BACK_LABEL, BACK_URI } from '../../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../../components/i18n';
import type { Exercise as ExerciseType, InjectResultOverviewOutput } from '../../../../../utils/api-types';
import useEnterpriseEdition from '../../../../../utils/hooks/useEnterpriseEdition';
import { externalContractTypesWithFindings } from '../../../../../utils/injector_contract/InjectorContractUtils';
import EEChip from '../../../common/entreprise_edition/EEChip';

const useStyles = makeStyles()(theme => ({
  item: {
    height: 30,
    fontSize: 13,
    float: 'left',
    paddingRight: theme.spacing(1),
  },
}));

interface Props {
  injectResultOverview: InjectResultOverviewOutput;
  exercise: ExerciseType;
  backlabel?: string | null;
  backuri?: string | null;
}

const InjectIndexTabs = ({ injectResultOverview, exercise, backlabel, backuri }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const tabValue = location.pathname;

  const computePath = (path: string) => {
    if (backlabel && backuri) {
      return path + `?${BACK_LABEL}=${backlabel}&${BACK_URI}=${backuri}`;
    }
    return path;
  };

  const {
    isValidated: isValidatedEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const handleRemediationClick = (event: React.SyntheticEvent) => {
    event.preventDefault();
    if (!isValidatedEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Remediation'));
      openEnterpriseEditionDialog();
    } else {
      navigate(`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/remediations`);
    }
  };

  return (
    <Tabs value={tabValue}>
      <Tab
        component={Link}
        to={computePath(`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}`)}
        value={`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}`}
        label={t('Overview')}
        className={classes.item}
      />
      {(injectResultOverview.inject_injector_contract?.injector_contract_payload
        || externalContractTypesWithFindings.includes(injectResultOverview.inject_type ?? '')) && (
        <Tab
          component={Link}
          to={computePath(`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/findings`)}
          value={`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/findings`}
          label={t('Findings')}
          className={classes.item}
        />
      )}
      <Tab
        component={Link}
        to={computePath(`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/detail`)}
        value={`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/detail`}
        label={t('Inject Execution details')}
        className={classes.item}
      />
      {injectResultOverview.inject_injector_contract?.injector_contract_payload && (
        <Tab
          component={Link}
          to={computePath(`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/payload_info`)}
          value={`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/payload_info`}
          label={t('Payload info')}
          className={classes.item}
        />
      )}
      {injectResultOverview.inject_injector_contract?.injector_contract_payload && (
        <Tab
          component={Link}
          to={computePath(`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/remediations`)}
          onClick={handleRemediationClick}
          value={computePath(`/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}/remediations`)}
          label={(
            <Box display="flex" alignItems="center">
              {t('Remediations')}
              {!isValidatedEnterpriseEdition && (
                <EEChip
                  style={{ marginLeft: theme.spacing(1) }}
                  clickable
                  featureDetectedInfo={t('Remediation')}
                />
              )}
            </Box>
          )}
          className={classes.item}
        />
      )}
    </Tabs>
  );
};
export default InjectIndexTabs;
