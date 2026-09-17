import { Button, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { PublicOutlined } from '@mui/icons-material';
import { Target } from 'mdi-material-ui';
import { useContext } from 'react';
import { useNavigate, useParams } from 'react-router';

import { type PhishingLandingPagesHelper } from '../../../../../actions/phishing/phishing-helper';
import { DetailHero } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type PhishingLandingPage } from '../../../../../utils/api-types';
import { AbilityContext } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import PhishingLandingPagePopover from './PhishingLandingPagePopover';

const PhishingLandingPageHeader = () => {
  const { landingPageId } = useParams() as { landingPageId: PhishingLandingPage['phishing_landing_page_id'] };
  const { t } = useFormatter();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);
  const { landingPage } = useHelper(
    (helper: PhishingLandingPagesHelper) => ({ landingPage: helper.getPhishingLandingPage(landingPageId) as PhishingLandingPage }),
  );

  // The landing page's synthesized injector contract shares its id, so the
  // atomic testing creation deep link lands directly on the pre-filled
  // configuration form for this page.
  const canCreateAtomicTesting = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);

  return (
    <DetailHero
      iconNode={<PublicOutlined />}
      overline={t('Phishing landing page')}
      title={landingPage.phishing_landing_page_name ?? '-'}
      action={(
        <>
          {canCreateAtomicTesting && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button priority="secondary" startIcon={<Target fontSize="small" />} onClick={() => navigate(`/admin/atomic_testings/create/${landingPageId}`)} data-testid="landing-page-create-atomic-testing-button">
                  {t('Create atomic test')}
                </Button>
              </TooltipTrigger>
              <TooltipContent>{t('Create an atomic testing that sends a phishing campaign using this landing page')}</TooltipContent>
            </Tooltip>
          )}
          <PhishingLandingPagePopover landingPage={landingPage} />
        </>
      )}
    />
  );
};

export default PhishingLandingPageHeader;
