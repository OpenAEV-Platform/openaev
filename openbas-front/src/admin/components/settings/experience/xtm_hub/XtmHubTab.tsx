import { Button } from '@mui/material';
import type React from 'react';
import { useCallback, useMemo, useState } from 'react';

import { registerPlatform, unregisterPlatform } from '../../../../../actions/xtmhub/xtmhub-actions';
import { useFormatter } from '../../../../../components/i18n';
import { MESSAGING$ } from '../../../../../utils/Environment';
import { useAppDispatch } from '../../../../../utils/hooks';
import useAuth from '../../../../../utils/hooks/useAuth';
import useExternalTab from '../../../../../utils/hooks/useExternalTab';
import GradientButton from '../../../common/GradientButton';
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

const XtmHubTab: React.FC = () => {
  const { t } = useFormatter();
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const { settings } = useAuth();
  const isEnterpriseEdition = settings.platform_license?.license_is_validated === true;
  const registrationHubUrl = settings?.xtm_hub_url ?? 'https://hub.filigran.io';
  const [processStep, setProcessStep] = useState<ProcessSteps>(
    ProcessSteps.INSTRUCTIONS,
  );
  const dispatch = useAppDispatch();
  const [operationType, setOperationType] = useState<OperationType | null>(
    null,
  );

  const isRegistered = settings.xtm_hub_registration_status === 'registered';
  const platformInformation = {
    platform_url: window.location.origin,
    platform_title: settings?.platform_name ?? 'OpenAEV Platform',
    platform_id: settings?.platform_id ?? '',
    platform_contract: isEnterpriseEdition ? 'EE' : 'CE',
    platform_version: settings?.platform_version ?? '',
  };
  const queryPlatformInformation = new URLSearchParams(
    platformInformation,
  ).toString();

  const registrationUrl = `${registrationHubUrl}/redirect/register-openaev?${queryPlatformInformation}`;
  const unregistrationUrl = `${registrationHubUrl}/redirect/unregister-openaev?platform_id=${settings?.platform_id ?? ''}`;

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
        MESSAGING$.notifySuccess(t('Your OpenAEV platform is successfully registered'));
      },
    ).catch(() => {
      setProcessStep(ProcessSteps.ERROR);
    });
  };

  const handleUnregistration = () => {
    dispatch(unregisterPlatform()).then(
      () => {
        setIsDialogOpen(false);
        setShowConfirmation(false);
        setProcessStep(ProcessSteps.INSTRUCTIONS);
        setOperationType(null);
        MESSAGING$.notifySuccess(t('Your OpenAEV platform is successfully unregistered'));
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

  const handleOpenDialog = () => {
    setOperationType(
      isRegistered ? OperationType.UNREGISTER : OperationType.REGISTER,
    );
    setIsDialogOpen(true);
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

  const config = useMemo(() => {
    const isUnregister = operationType === OperationType.UNREGISTER;
    const messages = {
      register: {
        dialogTitle: t('Registering your platform...'),
        errorMessage: t('Sorry, we have an issue, please retry'),
        canceledMessage: t('You have canceled the registration process'),
        loaderButtonText: t('Continue registration'),
        confirmationTitle: t('Close registration process?'),
        confirmationMessage: t('registration_confirmation_dialog'),
        continueButtonText: t('Continue registration'),
        instructionKey: 'registration_instruction_paragraph',
      },
      unregister: {
        dialogTitle: t('Unregistering your platform...'),
        errorMessage: t('Sorry, we have an issue, please retry'),
        canceledMessage: t('You have canceled the unregistration process'),
        loaderButtonText: t('Continue unregistration'),
        confirmationTitle: t('Close unregistration process?'),
        confirmationMessage: t('unregistration_confirmation_dialog'),
        continueButtonText: t('Continue unregistration'),
        instructionKey: 'unregistration_instruction_paragraph',
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

  const getButtonText = () => {
    if (isRegistered) {
      return t('Unregister from XTM Hub');
    }
    return t('Register in XTM Hub');
  };

  if (isRegistered) {
    return (
      <>
        <Button
          variant="outlined"
          size="small"
          color="error"
          onClick={handleOpenDialog}
        >
          {getButtonText()}
        </Button>
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
  }

  return (
    <>
      <GradientButton
        size="small"
        title={getButtonText()}
        onClick={handleOpenDialog}
      >
        {getButtonText()}
      </GradientButton>
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
