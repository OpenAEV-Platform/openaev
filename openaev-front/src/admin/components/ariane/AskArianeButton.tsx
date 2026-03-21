import { SvgIcon } from '@mui/material';
import { LogoXtmOneIcon } from 'filigran-icon';
import { type FunctionComponent } from 'react';

import Button from '../../../components/common/button/Button';
import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import AskArianePanel from './AskArianePanel';
import { useChatbot } from './ChatbotContext';

const AskArianeButton: FunctionComponent = () => {
  const { t } = useFormatter();
  const { settings } = useAuth();
  const { isOpen, mode, toggleChat, closeChat, setMode, setSidebarWidth, setIsResizing } = useChatbot();

  if (!settings.platform_xtm_one_configured) {
    return null;
  }

  return (
    <>
      <Button
        variant="tertiary"
        gradient
        gradientVariant="ai"
        selected={isOpen}
        onClick={toggleChat}
        startIcon={(
          <SvgIcon
            component={LogoXtmOneIcon}
            inheritViewBox
            sx={{ fontSize: '16px !important' }}
          />
        )}
      >
        {t('Ask Ariane')}
      </Button>

      {isOpen && (
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
