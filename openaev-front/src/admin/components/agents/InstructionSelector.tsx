import { ContentCopyOutlined, TerminalOutlined } from '@mui/icons-material';
import { Alert, Button, FormControlLabel, Radio, RadioGroup } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Bash, DownloadCircleOutline, Powershell } from 'mdi-material-ui';
import { useState } from 'react';

import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../components/i18n';
import { type ExecutorOutput, type Token } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import { copyToClipboard, download } from '../../../utils/utils';

const USER = 'user';
const WINDOWS = 'Windows';
const MACOS = 'MacOS';
const LINUX = 'Linux';
const OPENAEV_AGENT = 'openaev_agent';

interface InstructionSelectorProps {
  userToken: Token;
  platform: string;
  selectedExecutor: ExecutorOutput;
}

const InstructionSelector: React.FC<InstructionSelectorProps> = ({ userToken, platform, selectedExecutor }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [selectedOption, setSelectedOption] = useState(USER);

  const tabEntries: TabsEntry[] = [{
    key: 'Standard Installation',
    label: t('Standard Installation'),
  }, {
    key: 'Advanced Installation',
    label: t('Advanced Installation'),
  }];
  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  const { settings } = useAuth();

  const handleOptionChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSelectedOption(event.target.value);
  };

  const buildInstallationUrl = (baseUrl: string) => {
    if (currentTab === 'Standard Installation') return `${baseUrl}/session-user/${userToken?.token_value}`;
    if (currentTab === 'Advanced Installation' && selectedOption === USER) return `${baseUrl}/service-user/${userToken?.token_value}`;
    return `${baseUrl}/service/${userToken?.token_value}`;
  };
  const buildOpenAEVInstallerScript = () => {
    const buildExtraParams = (advanced: string, standard: string, other: string) => {
      let result = other;
      if (currentTab === 'Advanced Installation' && selectedOption === USER) {
        result = advanced;
      } else if (currentTab === 'Standard Installation') {
        result = standard;
      }
      return result;
    };
    const buildUrlScript2Windows = () => {
      if (currentTab === 'Advanced Installation' && selectedOption === USER) {
        return `&([scriptblock]::Create((iwr ${buildInstallationUrl(settings.platform_base_url + '/api/agent/installer/openaev/windows')}))) ${buildExtraParams('-User USER -Password PASSWORD', '', '')}`;
      }
      return `iex (iwr ${buildInstallationUrl(settings.platform_base_url + '/api/agent/installer/openaev/windows')}).Content`;
    };

    switch (platform) {
      case WINDOWS:
        return {
          icon: <Powershell />,
          label: 'powershell',
          exclusions: '',
          displayedCode: buildUrlScript2Windows(),
          code: buildUrlScript2Windows(),
        };
      case LINUX:
        return {
          icon: <Bash />,
          label: 'sh',
          exclusions: '',
          displayedCode: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openaev/linux')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
          code: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openaev/linux')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
        };
      case MACOS:
        return {
          icon: <TerminalOutlined />,
          label: 'sh',
          exclusions: '',
          displayedCode: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openaev/macos')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
          code: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openaev/macos')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
        };
      default:
        return {
          icon: <Bash />,
          label: 'sh',
          exclusions: '',
          displayedCode: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openaev/linux')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
          code: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openaev/linux')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
        };
    }
  };
  const buildInstallationScriptsAndActionButtons = () => {
    const fileExtension = platform === WINDOWS ? 'ps1' : 'sh';
    const code = buildOpenAEVInstallerScript().code;
    const displayedCode = buildOpenAEVInstallerScript().displayedCode;

    const buildInstallationMessage = () => {
      let message = '';
      if (selectedExecutor?.executor_type === OPENAEV_AGENT) {
        if (currentTab === 'Standard Installation' && platform === WINDOWS) {
          message = t('Run the following PowerShell snippet in a standard prompt or download the .ps1 script.');
        } else if (currentTab === 'Standard Installation' && platform !== WINDOWS) {
          message = t('Run the following bash snippet in a terminal or download the .sh script.');
        } else if (currentTab !== 'Standard Installation' && platform === WINDOWS && selectedOption === USER) {
          message = `${t('To install, copy and paste the following PowerShell snippet into an elevated prompt or download the .ps1 script.')} ${t('It can be run as administrator or as a standard user depending on the user rights used in the script parameters.')}`;
        } else if (currentTab !== 'Standard Installation' && platform === WINDOWS && selectedOption !== USER) {
          message = `${t('To install, copy and paste the following PowerShell snippet into an elevated prompt or download the .ps1 script and run it with administrator privileges.')} ${t('Installing it as a system grants system-wide privileges.')}`;
        } else if (currentTab !== 'Standard Installation' && platform !== WINDOWS && selectedOption === USER) {
          message = `${t('To install, copy and paste the following bash snippet into a terminal with root privileges, or download the .sh script and run it as root.')} ${t('It can be run as administrator or as a standard user depending on the user rights used in the script parameters.')}`;
        } else if (currentTab !== 'Standard Installation' && platform !== WINDOWS && selectedOption !== USER) {
          message = `${t('To install, copy and paste the following bash snippet into a terminal with root privileges, or download the .sh script and run it as root.')} ${t(`Installing it as a system grants system-wide privileges.`)}`;
        }
      } else if (selectedExecutor?.executor_type !== OPENAEV_AGENT && platform === WINDOWS) {
        message = t('You can whether directly copy and paste the following Powershell snippet in an elevated prompt or download the .ps1 script (and execute it as an administrator).');
      } else if (selectedExecutor?.executor_type !== OPENAEV_AGENT && platform !== WINDOWS) {
        message = t('You can whether directly copy and paste the following bash snippet in a root console or download the .sh script (and execute it as root).');
      }
      return (
        <p>{message}</p>
      );
    };

    return (
      <>
        {buildInstallationMessage()}
        <pre style={{ margin: theme.spacing(2, 0, 1) }}>{displayedCode}</pre>
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          gap: 8,
        }}
        >
          <Button
            variant="outlined"
            style={{ marginBottom: theme.spacing(2) }}
            startIcon={<ContentCopyOutlined />}
            onClick={() => copyToClipboard(t, code)}
          >
            {t('Copy')}
          </Button>
          <Button
            variant="outlined"
            style={{ marginBottom: theme.spacing(2) }}
            startIcon={<DownloadCircleOutline />}
            onClick={() => download(displayedCode, `openaev.${fileExtension}`, 'text/plain')}
          >
            {t('Download')}
          </Button>
        </div>
      </>
    );
  };
  const buildStandardInstallation = () => {
    return (
      platform && (
        <>
          <Alert
            variant="outlined"
            severity="info"
            style={{ marginTop: theme.spacing(2) }}
          >
            {`${t('The agent runs in the background as a session and only executes when the user is logged in and active.')} ${t('For further details, refer to the')} `}
            <a target="_blank" href={selectedExecutor?.executor_doc} rel="noreferrer">
              {t('{executor_name} documentation.', { executor_name: selectedExecutor?.executor_name })}
            </a>
          </Alert>
          <p>
            {`${t('Install the agent using your own user account.')} ${platform === WINDOWS ? t('It can be run as administrator or as a standard user, depending on the PowerShell elevation.') : t('This installation requires only local standard privileges.')}`}
          </p>
          {buildInstallationScriptsAndActionButtons()}
        </>
      ));
  };
  const buildAdvancedInstallation = () => {
    return (
      <>
        <Alert
          variant="outlined"
          severity="info"
          style={{ marginTop: theme.spacing(2) }}
        >
          {`${t('The agent runs in the background as a service and starts automatically when the machine powers on.')} ${t('For further details, refer to the')} `}
          <a target="_blank" href={selectedExecutor?.executor_doc} rel="noreferrer">
            {t('{executor_name} documentation.', { executor_name: selectedExecutor?.executor_name })}
          </a>
        </Alert>
        <p>
          {`${t('Install the agent as a user or a system service. This installation requires local administrator privileges.')}`}
        </p>
        <div>
          <RadioGroup
            value={selectedOption}
            onChange={handleOptionChange}
            style={{
              display: 'flex',
              flexDirection: 'row',
              gap: '20px',
            }}
          >
            <FormControlLabel value="user" control={<Radio />} label={t('Install Agent as User')} />
            <FormControlLabel value="system" control={<Radio />} label={t('Install Agent as System')} />
          </RadioGroup>
        </div>
        {
          selectedOption === USER && platform === WINDOWS && (
            <Alert
              variant="outlined"
              severity="info"
              style={{ marginTop: theme.spacing(1) }}
            >
              {`${t('You should add "Log on as a service" policy if you are installing as a user.')} ${t('For further details, refer to the')} `}
              <a target="_blank" href="https://learn.microsoft.com/en-us/system-center/scsm/enable-service-log-on-sm?view=sc-sm-2025" rel="noreferrer">
                {t('Windows documentation.')}
              </a>
            </Alert>
          )
        }
        {
          platform && (buildInstallationScriptsAndActionButtons())
        }
      </>
    );
  };

  return (
    <div>
      {selectedExecutor && (
        <div style={{ padding: theme.spacing(0, 2, 1) }}>

          {/* OAEV */}
          {selectedExecutor && selectedExecutor.executor_type === OPENAEV_AGENT && (
            <div>
              <Tabs
                entries={tabEntries}
                currentTab={currentTab}
                onChange={newValue => handleChangeTab(newValue)}
              />
              {currentTab === 'Standard Installation' && (buildStandardInstallation())}
              {currentTab === 'Advanced Installation' && (buildAdvancedInstallation())}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default InstructionSelector;
