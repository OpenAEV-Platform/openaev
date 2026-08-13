import { Button, Chip, Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@filigran/design-system';
import { SvgIcon } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { useContext, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import FiligranAiCguDialog from './FiligranAiCguDialog';
import { useChatbot } from './useChatbotHooks';
import isXtmOneAvailable from './xtmOneAvailability';

const AskArianeButton = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { isOpen, toggleChat } = useChatbot();
  const ability = useContext(AbilityContext);
  const [cguDialogOpen, setCguDialogOpen] = useState(false);
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const chatbotCguStatus = settings.filigran_chatbot_ai_cgu_status;
  const isCguPending = chatbotCguStatus === 'pending' || chatbotCguStatus === undefined;

  // Hide if the chatbot has been explicitly disabled, or if XTM One is not
  // connected properly (configured url + token, valid http(s) URL): the chat
  // proxy (`/api/xtmone/chat/*`) rejects every call in that case, so the
  // button cannot lead anywhere. Same gating as the CTEM Command Center
  // shortcut next to it (shared `isXtmOneAvailable` predicate).
  if (!isXtmOneAvailable(settings)) {
    return null;
  }

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS);

  const handleClick = () => {
    if (!isEnterpriseEdition) {
      // No EE license - open EE dialog
      setEEFeatureDetectedInfo(t('XTM One (Agentic IA)'));
      openEnterpriseEditionDialog();
    } else if (chatbotCguStatus === 'enabled') {
      // CGU accepted - normal toggle behavior
      toggleChat();
    } else if (canManage) {
      // CGU pending - show CGU dialog
      setCguDialogOpen(true);
    }
  };

  // The library's `ia` variant at tertiary priority IS this button's design.
  const buttonContent = (
    <Button
      variant="ia"
      priority="tertiary"
      onClick={handleClick}
      // FDS-WORKAROUND #23: open state via the class the library's active state uses — remove when `Button` gets `active` — see fds-migration/LIBRARY-FEEDBACK.md
      className={isOpen ? 'bg-filigran-ia-secondary-transparency' : undefined}
      startIcon={(
        <SvgIcon
          component={LogoXtmOneIcon}
          inheritViewBox
          sx={{
            fontSize: '20px !important',
            color: theme.palette.ai.main,
          }}
        />
      )}
      // Decorative: the button owns the behaviour and the accessible name.
      endIcon={!isEnterpriseEdition ? <span aria-hidden="true"><Chip label={t('EE')} severity="ee" /></span> : undefined}
    >
      {t('Ask Ariane')}
    </Button>
  );

  // If CGU pending and user cannot manage, wrap with tooltip explaining
  if (isEnterpriseEdition && isCguPending && !canManage) {
    return (
      <TooltipProvider delayDuration={200}>
        <Tooltip>
          <TooltipTrigger asChild>
            <span>{buttonContent}</span>
          </TooltipTrigger>
          <TooltipContent>
            {t('Ask Ariane isn\'t activated yet. Please reach out to your administrator to enable this feature.')}
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  return (
    <>
      {buttonContent}
      {cguDialogOpen && (
        <FiligranAiCguDialog
          open={cguDialogOpen}
          onClose={() => setCguDialogOpen(false)}
        />
      )}
    </>
  );
};

export default AskArianeButton;
