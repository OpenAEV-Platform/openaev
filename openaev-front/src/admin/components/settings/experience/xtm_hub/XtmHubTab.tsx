import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import type React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import type { LoggedHelper } from '../../../../../actions/helper';
import { registerPlatform, unregisterPlatform } from '../../../../../actions/xtmhub/xtmhub-actions';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { isDemoInstance, MESSAGING$, XTM_HUB_DEFAULT_URL } from '../../../../../utils/Environment';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAuth from '../../../../../utils/hooks/useAuth';
import useExternalTab from '../../../../../utils/hooks/useExternalTab';
import { getCurrentTenantId } from '../../../../../utils/url-helper';
import { XTM_HUB_AUTO_REGISTER_QUERY_PARAM } from '../../../xtm_hub/XtmHubRedirect';
import XtmHubConfirmationDialog from './XtmHubConfirmationDialog';
import XtmHubProcessDialog from './XtmHubProcessDialog';
import XtmHubProcessInstructions from './XtmHubProcessInstructions';
import XtmHubProcessLoader from './XtmHubProcessLoader';

enum ProcessSteps {
  INSTRUCTIONS = 'INSTRUCTIONS',
  WAITING_HUB = 'WAITING_HUB',
  ERROR = 'ERROR',
  CANCELED = 'CANCELED',
}

enum OperationType {
  REGISTER = 'register',
  UNREGISTER = 'unregister',
}

interface XtmHubTabProps { renderTrigger?: (handleOpen: () => void) => React.ReactNode }

const XtmHubTab: React.FC<XtmHubTabProps> = ({ renderTrigger }) => {
  const { t } = useFormatter();
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [isAutoRegistrationPromptOpen, setIsAutoRegistrationPromptOpen] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { settings, currentUserTenant } = useAuth();
  const registration = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());
  const isEnterpriseEdition = settings.platform_license?.license_is_validated === true;
  const isDemoMode = isDemoInstance(settings);
  const registrationHubUrl = settings?.xtm_hub_url ?? XTM_HUB_DEFAULT_URL;
  const registrationPlatformTitle = settings?.platform_name ?? 'OpenAEV Platform';
  const [processStep, setProcessStep] = useState<ProcessSteps>(
    ProcessSteps.INSTRUCTIONS,
  );
  const dispatch = useAppDispatch();
  const [operationType, setOperationType] = useState<OperationType | null>(
    null,
  );

  const isRegistered = registration?.tenant_xtmhub_registration_status === 'REGISTERED';
  const platformIdentifiers = {
    tenant_id: getCurrentTenantId(),
    platform_id: settings?.platform_id ?? '',
  };
  const platformInformation = {
    ...platformIdentifiers,
    // platform_url should not end with / to avoid issues with one click deploy
    platform_url: `${window.location.origin}/${platformIdentifiers.tenant_id}`,
    platform_title: registrationPlatformTitle,
    platform_contract: isEnterpriseEdition ? 'EE' : 'CE',
    platform_version: settings?.platform_version ?? '',
    tenant_name: currentUserTenant?.tenant_name ?? '',
  };
  const queryPlatformIdentifiers = new URLSearchParams(
    platformIdentifiers,
  ).toString();
  const queryPlatformInformation = new URLSearchParams(
    platformInformation,
  ).toString();

  const registrationUrl = `${registrationHubUrl}/redirect/register-openaev?${queryPlatformInformation}`;
  const unregistrationUrl = `${registrationHubUrl}/redirect/unregister-openaev?${queryPlatformIdentifiers}`;

  const handleClosingTab = () => {
    setProcessStep(ProcessSteps.CANCELED);
  };

  const handleRegistration = (token: string) => {
    dispatch(registerPlatform(token)).then(
      () => {
        setIsDialogOpen(false);
        setShowConfirmation(false);
        setProcessStep(ProcessSteps.INSTRUCTIONS);
        setOperationType(null);
        MESSAGING$.notifySuccess(t('Your OpenAEV platform is successfully connected'));
      },
    ).catch(() => {
      setProcessStep(ProcessSteps.ERROR);
    });
  };

  const handleUnregistration = () => {
    dispatch(unregisterPlatform(registration?.tenant_xtmhub_registration_id ?? '')).then(
      () => {
        setIsDialogOpen(false);
        setShowConfirmation(false);
        setProcessStep(ProcessSteps.INSTRUCTIONS);
        setOperationType(null);
        MESSAGING$.notifySuccess(t('Your OpenAEV platform is successfully disconnected'));
      },
    ).catch(() => {
      setProcessStep(ProcessSteps.ERROR);
    });
  };

  const handleTabMessage = useCallback(
    (event: MessageEvent) => {
      const eventData = event.data;
      const { action, token } = eventData;
      if (action === 'register') {
        setOperationType(OperationType.REGISTER);
        handleRegistration(token);
      } else if (action === 'unregister') {
        setOperationType(OperationType.UNREGISTER);
        handleUnregistration();
      } else if (action === 'cancel') {
        setProcessStep(ProcessSteps.CANCELED);
      } else {
        setProcessStep(ProcessSteps.ERROR);
      }
    },
    [handleRegistration, handleUnregistration, settings?.platform_id],
  );

  const { openTab, closeTab, focusTab } = useExternalTab({
    url: isRegistered ? unregistrationUrl : registrationUrl,
    tabName: isRegistered ? 'xtmhub-unregistration' : 'xtmhub-registration',
    onMessage: handleTabMessage,
    onClosingTab: handleClosingTab,
  });

  const clearAutoRegisterQueryParams = useCallback(() => {
    const searchParams = new URLSearchParams(location.search);
    if (!searchParams.has(XTM_HUB_AUTO_REGISTER_QUERY_PARAM)) {
      return;
    }
    searchParams.delete(XTM_HUB_AUTO_REGISTER_QUERY_PARAM);
    const targetSearch = searchParams.toString();
    navigate(
      {
        pathname: location.pathname,
        search: targetSearch ? `?${targetSearch}` : '',
      },
      { replace: true },
    );
  }, [location.pathname, location.search, navigate]);

  const handleCancelAutoRegistration = () => {
    setIsAutoRegistrationPromptOpen(false);
    setProcessStep(ProcessSteps.INSTRUCTIONS);
    setOperationType(null);
  };

  const handleCancelClose = () => {
    setShowConfirmation(false);
  };

  const handleCloseDialog = () => {
    closeTab();
    setIsDialogOpen(false);
    setShowConfirmation(false);
    setProcessStep(ProcessSteps.INSTRUCTIONS);
    setOperationType(null);
  };

  const handleAttemptClose = () => {
    // If tab is open, show confirmation dialog
    if (processStep === ProcessSteps.WAITING_HUB) {
      setShowConfirmation(true);
    } else {
      handleCloseDialog();
    }
  };

  const handleWaitingHubStep = () => {
    openTab();
    setProcessStep(ProcessSteps.WAITING_HUB);
  };

  const handleConfirmAutoRegistration = () => {
    setIsAutoRegistrationPromptOpen(false);
    setOperationType(OperationType.REGISTER);
    setIsDialogOpen(true);
    handleWaitingHubStep();
  };
  const handleOpenDialog = () => {
    setOperationType(
      isRegistered ? OperationType.UNREGISTER : OperationType.REGISTER,
    );
    setIsDialogOpen(true);
  };

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    if (!searchParams.has(XTM_HUB_AUTO_REGISTER_QUERY_PARAM)) {
      return;
    }
    const shouldAutoRegister = searchParams.get(XTM_HUB_AUTO_REGISTER_QUERY_PARAM) === 'true';
    clearAutoRegisterQueryParams();
    if (isDemoMode || isRegistered || !shouldAutoRegister) {
      return;
    }
    setOperationType(OperationType.REGISTER);
    setProcessStep(ProcessSteps.INSTRUCTIONS);
    setIsAutoRegistrationPromptOpen(true);
  }, [clearAutoRegisterQueryParams, isDemoMode, isRegistered, location.search]);

  const config = useMemo(() => {
    const isUnregister = operationType === OperationType.UNREGISTER;
    const messages = {
      register: {
        dialogTitle: t('Connecting your product...'),
        errorMessage: t('Sorry, we have an issue, please retry'),
        canceledMessage: t('You have canceled the connection process'),
        loaderButtonText: t('Continue connection'),
        confirmationTitle: t('Close connection process?'),
        confirmationMessage: t('connection_confirmation_dialog'),
        continueButtonText: t('Continue connection'),
        instructionKey: 'connection_instruction_paragraph',
      },
      unregister: {
        dialogTitle: t('Disconnecting your product...'),
        errorMessage: t('Sorry, we have an issue, please retry'),
        canceledMessage: t('You have canceled the disconnection process'),
        loaderButtonText: t('Continue disconnection'),
        confirmationTitle: t('Close disconnection process?'),
        confirmationMessage: t('disconnection_confirmation_dialog'),
        continueButtonText: t('Continue disconnection'),
        instructionKey: 'disconnection_instruction_paragraph',
      },
    };
    return isUnregister ? messages.unregister : messages.register;
  }, [operationType, t]);

  const renderDialogContent = () => {
    const PROCESS_RENDERERS = new Map([
      [
        ProcessSteps.INSTRUCTIONS,
        () => (
          <XtmHubProcessInstructions
            onContinue={handleWaitingHubStep}
            instructionKey={config.instructionKey}
          />
        ),
      ],
      [
        ProcessSteps.WAITING_HUB,
        () => (
          <XtmHubProcessLoader
            onFocusTab={focusTab}
            buttonText={config.loaderButtonText}
          />
        ),
      ],
      [ProcessSteps.ERROR, () => <div>{config.errorMessage}</div>],
      [ProcessSteps.CANCELED, () => <div>{config.canceledMessage}</div>],
    ]);
    const renderer = PROCESS_RENDERERS.get(processStep);
    return renderer && isDialogOpen ? renderer() : null;
  };

  if (isDemoMode) return null;

  return (
    <>
      <Dialog
        open={isAutoRegistrationPromptOpen}
        onClose={handleCancelAutoRegistration}
        aria-labelledby="xtm-hub-auto-registration-title"
        aria-describedby="xtm-hub-auto-registration-description"
      >
        <DialogTitle id="xtm-hub-auto-registration-title">{t('Authorize connection')}</DialogTitle>
        <DialogContent>
          <DialogContentText id="xtm-hub-auto-registration-description">
            {t('Allow OpenAEV to connect with XTM Hub')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" onClick={handleCancelAutoRegistration} color="primary">
            {t('Cancel')}
          </Button>
          <Button onClick={handleConfirmAutoRegistration} color="primary" autoFocus>
            {t('Continue')}
          </Button>
        </DialogActions>
      </Dialog>
      {renderTrigger?.(handleOpenDialog)}
      <XtmHubProcessDialog
        open={isDialogOpen}
        title={config.dialogTitle}
        onClose={handleAttemptClose}
      >
        {renderDialogContent()}
      </XtmHubProcessDialog>
      <XtmHubConfirmationDialog
        open={showConfirmation}
        title={config.confirmationTitle}
        message={config.confirmationMessage}
        confirmButtonText={t('Yes, close')}
        cancelButtonText={config.continueButtonText}
        onConfirm={handleCloseDialog}
        onCancel={handleCancelClose}
      />
    </>
  );
};

export default XtmHubTab;
