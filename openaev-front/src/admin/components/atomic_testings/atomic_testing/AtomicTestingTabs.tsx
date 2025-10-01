import { Box, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Link, useLocation, useNavigate } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import type { InjectResultOverviewOutput } from '../../../../utils/api-types';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { externalContractTypesWithFindings } from '../../../../utils/injector_contract/InjectorContractUtils';
import EEChip from '../../common/entreprise_edition/EEChip';

const useStyles = makeStyles()(theme => ({
  item: {
    height: 30,
    fontSize: 13,
    float: 'left',
    paddingRight: theme.spacing(1),
  },
}));

interface Props { injectResultOverview: InjectResultOverviewOutput }

const AtomicTestingTabs = ({ injectResultOverview }: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();

  const {
    isValidated: isValidatedEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  let tabValue = location.pathname;
  if (location.pathname.includes(`/admin/atomic_testings/${injectResultOverview.inject_id}/detail`)) {
    tabValue = `/admin/atomic_testings/${injectResultOverview.inject_id}/detail`;
  }

  const handleRemediationClick = (event: React.SyntheticEvent) => {
    event.preventDefault();
    if (!isValidatedEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Remediation'));
      openEnterpriseEditionDialog();
    } else {
      navigate(`/admin/atomic_testings/${injectResultOverview.inject_id}/remediations`);
    }
  };

  return (
    <Tabs value={tabValue}>
      <Tab
        component={Link}
        to={`/admin/atomic_testings/${injectResultOverview.inject_id}`}
        value={`/admin/atomic_testings/${injectResultOverview.inject_id}`}
        label={t('Overview')}
        className={classes.item}
      />
      {(injectResultOverview.inject_injector_contract?.injector_contract_payload
        || externalContractTypesWithFindings.includes(injectResultOverview.inject_type ?? '')) && (
        <Tab
          component={Link}
          to={`/admin/atomic_testings/${injectResultOverview.inject_id}/findings`}
          value={`/admin/atomic_testings/${injectResultOverview.inject_id}/findings`}
          label={t('Findings')}
          className={classes.item}
        />
      )}
      <Tab
        component={Link}
        to={`/admin/atomic_testings/${injectResultOverview.inject_id}/detail`}
        value={`/admin/atomic_testings/${injectResultOverview.inject_id}/detail`}
        label={t('Inject Execution details')}
        className={classes.item}
      />
      {injectResultOverview.inject_injector_contract?.injector_contract_payload && (
        <Tab
          component={Link}
          to={`/admin/atomic_testings/${injectResultOverview.inject_id}/payload_info`}
          value={`/admin/atomic_testings/${injectResultOverview.inject_id}/payload_info`}
          label={t('Payload info')}
          className={classes.item}
        />
      )}
      {injectResultOverview.inject_injector_contract?.injector_contract_payload && (
        <Tab
          component={Link}
          to={`/admin/atomic_testings/${injectResultOverview.inject_id}/remediations`}
          onClick={handleRemediationClick}
          value={`/admin/atomic_testings/${injectResultOverview.inject_id}/remediations`}
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
export default AtomicTestingTabs;
