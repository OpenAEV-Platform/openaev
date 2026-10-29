import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { Link, useLocation, useNavigate } from 'react-router';

import { BACK_LABEL, BACK_URI } from '../../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../../components/i18n';
import type { Exercise as ExerciseType, InjectResultOverviewOutput } from '../../../../../utils/api-types';
import useEnterpriseEdition from '../../../../../utils/hooks/useEnterpriseEdition';
import { externalContractTypesWithFindings } from '../../../../../utils/injector_contract/InjectorContractUtils';
import EEChip from '../../../common/entreprise_edition/EEChip';

interface Props {
  injectResultOverview: InjectResultOverviewOutput;
  exercise: ExerciseType;
  backlabel?: string | null;
  backuri?: string | null;
}

const InjectIndexTabs = ({ injectResultOverview, exercise, backlabel, backuri }: Props) => {
  const { t } = useFormatter();
  const location = useLocation();
  const navigate = useNavigate();
  const base = `/admin/simulations/${exercise.exercise_id}/injects/${injectResultOverview.inject_id}`;
  const tabValue = location.pathname;
  const current = (path: string) => (tabValue === path ? 'page' : undefined);
  const hasPayload = !!injectResultOverview.inject_injector_contract?.injector_contract_payload;

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
      navigate(`${base}/remediations`);
    }
  };

  // Route-based tabs: the panels are the routed pages, so every tab is a real link.
  return (
    <Tabs value={tabValue} panels="external">
      <TabsList>
        <TabsTrigger value={base} asChild>
          <Link to={computePath(base)} aria-current={current(base)}>{t('Overview')}</Link>
        </TabsTrigger>
        <TabsTrigger value={`${base}/execution_details`} asChild>
          <Link to={computePath(`${base}/execution_details`)} aria-current={current(`${base}/execution_details`)}>{t('Execution details')}</Link>
        </TabsTrigger>
        {hasPayload && (
          <TabsTrigger value={`${base}/payload_info`} asChild>
            <Link to={computePath(`${base}/payload_info`)} aria-current={current(`${base}/payload_info`)}>{t('Action details')}</Link>
          </TabsTrigger>
        )}
        {(hasPayload || externalContractTypesWithFindings.includes(injectResultOverview.inject_type ?? '')) && (
          <TabsTrigger value={`${base}/findings`} asChild>
            <Link to={computePath(`${base}/findings`)} aria-current={current(`${base}/findings`)}>{t('Findings')}</Link>
          </TabsTrigger>
        )}
        {hasPayload && (
          <TabsTrigger value={`${base}/remediations`} asChild>
            <Link to={computePath(`${base}/remediations`)} onClick={handleRemediationClick} aria-current={current(`${base}/remediations`)}>
              {t('Remediations')}
              {!isValidatedEnterpriseEdition && <EEChip />}
            </Link>
          </TabsTrigger>
        )}
      </TabsList>
    </Tabs>
  );
};
export default InjectIndexTabs;
