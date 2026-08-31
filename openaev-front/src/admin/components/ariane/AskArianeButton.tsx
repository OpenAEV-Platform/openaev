import { Button, SvgIcon, Tooltip } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { LogoXtmOneIcon } from 'filigran-icon';
import { useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import EEChip from '../common/entreprise_edition/EEChip';
import FiligranAiCguDialog from './FiligranAiCguDialog';
import { useChatbot } from './useChatbotHooks';
import isXtmOneAvailable from './xtmOneAvailability';

const AskArianeButton = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { isOpen, toggleChat } = useChatbot();
  const ability = useAbility();
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

  // AI gradient (aligned with OpenCTI's "Ask Ariane" tertiary gradient button):
  // borderless, transparent background, gradient-painted label + icon, subtle
  // AI-tinted hover. No outlined box.
  const aiGradient = `linear-gradient(90deg, ${theme.palette.ai.light} 0%, ${theme.palette.ai.main} 100%)`;

  const buttonContent = (
    <Button
      variant="text"
      onClick={handleClick}
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
      endIcon={!isEnterpriseEdition ? <span><EEChip /></span> : undefined}
      sx={{
        'height': 36,
        'paddingInline': 1.5,
        'borderRadius': 1,
        'fontWeight': 600,
        'whiteSpace': 'nowrap',
        'backgroundColor': isOpen ? alpha(theme.palette.ai.main, 0.15) : 'transparent',
        '&:hover': { backgroundColor: alpha(theme.palette.ai.main, 0.15) },
        // Gradient-painted label, matching OpenCTI.
        '& .ariane-label': {
          background: aiGradient,
          backgroundClip: 'text',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
        },
        '& .MuiButton-startIcon': { marginRight: '6px' },
      }}
    >
      <span className="ariane-label">{t('Ask Ariane')}</span>
    </Button>
  );

  // If CGU pending and user cannot manage, wrap with tooltip explaining
  if (isEnterpriseEdition && isCguPending && !canManage) {
    return (
      <Tooltip title={t('Ask Ariane isn\'t activated yet. Please reach out to your administrator to enable this feature.')}>
        <span>{buttonContent}</span>
      </Tooltip>
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
