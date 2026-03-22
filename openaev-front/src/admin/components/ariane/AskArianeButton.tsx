import { SvgIcon } from '@mui/material';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent } from 'react';

import Button from '../../../components/common/button/Button';
import { useFormatter } from '../../../components/i18n';
import useAI from '../../../utils/hooks/useAI';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../common/entreprise_edition/EEChip';
import EETooltip from '../common/entreprise_edition/EETooltip';
import AskArianePanel from './AskArianePanel';
import { useChatbot } from './useChatbotHooks';

const AskArianeButton: FunctionComponent = () => {
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { isValidated: isEnterpriseEdition, openDialog: openEnterpriseEditionDialog, setEEFeatureDetectedInfo } = useEnterpriseEdition();
  const { enabled, configured } = useAI();
  const { isOpen, mode, toggleChat, closeChat, setMode, setSidebarWidth, setIsResizing } = useChatbot();

  const isAvailable = isEnterpriseEdition && enabled && configured;

  if (!settings.platform_xtm_one_configured) {
    return null;
  }

  const handleClick = () => {
    if (!isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t('Ariane AI'));
      openEnterpriseEditionDialog();
    } else {
      toggleChat();
    }
  };

  return (
    <>
      <EETooltip forAi title={`${t('Ask Ariane')}${!isAvailable ? ' (EE)' : ''}`}>
        <Button
          variant="tertiary"
          gradient
          gradientVariant="ai"
          selected={isOpen}
          onClick={handleClick}
          startIcon={(
            <SvgIcon
              component={LogoXtmOneIcon}
              inheritViewBox
              sx={{ fontSize: '16px !important' }}
            />
          )}
        >
          {t('Ask Ariane')}
          <EEChip />
        </Button>
      </EETooltip>

      {isAvailable && isOpen && (
        <AskArianePanel
          mode={mode}
          onClose={closeChat}
          onModeChange={setMode}
          onWidthChange={setSidebarWidth}
          onResizeStart={() => setIsResizing(true)}
          onResizeEnd={() => setIsResizing(false)}
        />
      )}
    </>
  );
};

export default AskArianeButton;
