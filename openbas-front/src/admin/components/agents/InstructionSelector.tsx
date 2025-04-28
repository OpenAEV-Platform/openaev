import { ContentCopyOutlined, TerminalOutlined } from '@mui/icons-material';
import { Alert, Button, FormControl, FormControlLabel, InputLabel, MenuItem, Radio, RadioGroup, Select, Tab, Tabs, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Bash, DownloadCircleOutline, Powershell } from 'mdi-material-ui';
import { useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type Executor, type Token } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import { copyToClipboard, download } from '../../../utils/utils';

const USER = 'user';
const WINDOWS = 'Windows';
const MACOS = 'MacOS';
const LINUX = 'Linux';
const x86_64 = 'x86_64';
const OPENBAS_CALDERA = 'openbas_caldera';
const OPENBAS_AGENT = 'openbas_agent';

interface InstructionSelectorProps {
  userToken: Token;
  platform: string;
  selectedExecutor: Executor;
}

const InstructionSelector: React.FC<InstructionSelectorProps> = ({ userToken, platform, selectedExecutor }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedOption, setSelectedOption] = useState(USER);
  const [agentFolder] = useState<null | string>(null);
  const [arch, setArch] = useState<string>(x86_64);

  const { settings } = useAuth();

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };
  const handleOptionChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSelectedOption(event.target.value);
  };

  const buildInstallationUrl = (baseUrl: string) => {
    if (activeTab === 0) return `${baseUrl}/session-user/${userToken?.token_value}`;
    if (activeTab === 1 && selectedOption === USER) return `${baseUrl}/service-user/${userToken?.token_value}`;
    return `${baseUrl}/service/${userToken?.token_value}`;
  };
  const buildCalderaInstallerScript = () => {
    switch (platform) {
      case WINDOWS:
        return {
          icon: <Powershell />,
          label: 'powershell',
          defaultAgentFolder: 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera',
          exclusions: `${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}
${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe
MD5: 68c1795fb45cb9b522d6cf48443fdc37
SHA1: 5f87d06f818ff8cba9e11e8cd1c6f9d990eca0f8
SHA256: 6b180913acb8cdac3fb8d3154a2f6a0bed13c056a477f4f94c4679414ec13b9f
SHA512: 6185b7253eedfa6253f26cd85c4bcfaf05195219b6ab06b43d9b07279d7d0cdd3c957bd58d36058d7cde405bc8c5084f3ac060a6080bfc18a843738d3bee87fd`,
          displayedCode: `$server="${settings.executor_caldera_public_url}";
$url="${settings.platform_base_url}/api/implant/caldera/windows/${arch}";
$wc=New-Object System.Net.WebClient;
$data=$wc.DownloadData($url);
get-process | ? {$_.modules.filename -like '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe'} | stop-process -f;
rm -force '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe' -ea ignore;
New-Item -ItemType Directory -Force -Path '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}' | Out-Null;
[io.file]::WriteAllBytes('${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe',$data) | Out-Null;
$data=$wc.DownloadData($url + "/ps1");
rm -force 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera\\obas-agent-caldera.ps1' -ea ignore;
[io.file]::WriteAllBytes('C:\\Program Files (x86)\\Filigran\\OBAS Caldera\\obas-agent-caldera.ps1',$data) | Out-Null;
New-NetFirewallRule -DisplayName "Allow OpenBAS" -Direction Inbound -Program '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe' -Action Allow | Out-Null;
New-NetFirewallRule -DisplayName "Allow OpenBAS" -Direction Outbound -Program '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe' -Action Allow | Out-Null;
Start-Process -FilePath '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe' -ArgumentList "-server $server -group red" -WindowStyle hidden;
schtasks /create /tn OpenBASCaldera /sc onlogon /rl highest /tr "Powershell -ExecutionPolicy Bypass -NoProfile -WindowStyle hidden -File 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera\\obas-agent-caldera.ps1' $server";`,
          code: `$server="${settings.executor_caldera_public_url}";$url="${settings.platform_base_url}/api/implant/caldera/windows/${arch}";$wc=New-Object System.Net.WebClient;$data=$wc.DownloadData($url);get-process | ? {$_.modules.filename -like '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe'} | stop-process -f;rm -force '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe' -ea ignore;New-Item -ItemType Directory -Force -Path '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}' | Out-Null;[io.file]::WriteAllBytes('${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe',$data) | Out-Null;$data=$wc.DownloadData($url + "/ps1");rm -force 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera\\obas-agent-caldera.ps1' -ea ignore;[io.file]::WriteAllBytes('C:\\Program Files (x86)\\Filigran\\OBAS Caldera\\obas-agent-caldera.ps1',$data) | Out-Null;New-NetFirewallRule -DisplayName "Allow OpenBAS" -Direction Inbound -Program '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe' -Action Allow | Out-Null;New-NetFirewallRule -DisplayName "Allow OpenBAS" -Direction Outbound -Program '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe' -Action Allow | Out-Null;Start-Process -FilePath '${agentFolder ?? 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera'}\\obas-agent-caldera.exe' -ArgumentList "-server $server -group red" -WindowStyle hidden;schtasks /create /tn OpenBASCaldera /sc onlogon /rl highest /tr "Powershell -ExecutionPolicy Bypass -NoProfile -WindowStyle hidden -File 'C:\\Program Files (x86)\\Filigran\\OBAS Caldera\\obas-agent-caldera.ps1' $server";`,
        };
      case LINUX:
        return {
          icon: <Bash />,
          label: 'sh',
          defaultAgentFolder: '/opt/openbas-caldera-agent',
          exclusions: `${agentFolder ?? '/opt/openbas-caldera-agent'}
${agentFolder ?? '/opt/openbas-caldera-agent/openbas-caldera-agent'}
MD5: d604c952bb3c6d96621594d39992c499
SHA1: 5b6087f87f5f2ae129f888bba799611836eb39a2
SHA256: 98d1e64445bbef46a36d4724699a386646de78881a1b6f2b346122c76d696c12
SHA512: ca07dc1d0a5297e29327e483f4f35dadb254d96a16a5c33da5ad048e6965a3863d621518a2be40f1a42226c68cbf5e779382a37ee5baa7dd7c538ec73ce059e8`,
          displayedCode: `server="${settings.executor_caldera_public_url}";
mkdir -p ${agentFolder ?? '/opt/openbas-caldera-agent'};
curl -s -X GET ${settings.platform_base_url}/api/implant/caldera/linux/${arch} > ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;
chmod +x ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;
nohup ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent -server $server -group red &`,
          code: `server="${settings.executor_caldera_public_url}";mkdir -p ${agentFolder ?? '/opt/openbas-caldera-agent'};curl -s -X GET ${settings.platform_base_url}/api/implant/caldera/linux/${arch} > ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;chmod +x ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;nohup ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent -server $server -group red &`,
        };
      case MACOS:
        return {
          icon: <TerminalOutlined />,
          label: 'sh',
          defaultAgentFolder: '/opt/openbas',
          exclusions: `${agentFolder ?? '/opt/openbas'}
${agentFolder ?? '/opt/openbas/openbas-caldera-agent'}
MD5: 1132906cc40001f51673108847b88d0c
SHA1: 3177df4a8fa13a2e13ce63670c579955ad55df3f
SHA256: 2b4397160925bf6b9dcca0949073fd9b2fc590ab641ea1d1c3d7d36048ed674a
SHA512: f1c8cf0c41c7d193bcb2aad21d7a739c785902c3231e15986b2eb37f911824a802f50cb2dbb509deba1c7a2a535fb7b34cf100303c61a6087102948628133747`,
          displayedCode: `server="${settings.executor_caldera_public_url}";
mkdir -p ${agentFolder ?? '/opt/openbas'};
curl -s -X GET ${settings.platform_base_url}/api/implant/caldera/macos/${arch} > ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;
chmod +x ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;
nohup ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent -server $server -group red &`,
          code: `server="${settings.executor_caldera_public_url}";mkdir -p ${agentFolder ?? '/opt/openbas-caldera-agent'};curl -s -X GET ${settings.platform_base_url}/api/implant/caldera/macos/${arch} > ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;chmod +x ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;nohup ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent -server $server -group red &`,
        };
      default:
        return {
          icon: <Bash />,
          label: 'sh',
          defaultAgentFolder: '/opt/openbas-caldera-agent',
          exclusions: `${agentFolder ?? '/opt/openbas-caldera-agent'}
${agentFolder ?? '/opt/openbas-caldera-agent/openbas-caldera-agent'}`,
          displayedCode: `server="${settings.executor_caldera_public_url}";
mkdir -p ${agentFolder ?? '/opt/openbas-caldera-agent'};
curl -s -X GET ${settings.platform_base_url}/api/implant/caldera/linux/${arch} > ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;
chmod +x ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;
nohup ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent -server $server -group red &`,
          code: `server="${settings.executor_caldera_public_url}";mkdir -p ${agentFolder ?? '/opt/openbas-caldera-agent'};curl -s -X GET ${settings.platform_base_url}/api/implant/caldera/linux/${arch} > ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;chmod +x ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent;nohup ${agentFolder ?? '/opt/openbas-caldera-agent'}/openbas-caldera-agent -server $server -group red &`,
        };
    }
  };
  const buildOpenBASInstallerScript = () => {
    const buildExtraParams = (advanced: string, standard: string, other: string) => {
      let result = other;
      if (activeTab === 1 && selectedOption === USER) {
        result = advanced;
      } else if (activeTab === 0) {
        result = standard;
      }
      return result;
    };
    const buildUrlScript2Windows = () => {
      if (activeTab === 1 && selectedOption === USER) {
        return `&([scriptblock]::Create((iwr ${buildInstallationUrl(settings.platform_base_url + '/api/agent/installer/openbas/windows')}))) ${buildExtraParams('-User USER -Password PASSWORD', '', '')}`;
      }
      return `iex (iwr ${buildInstallationUrl(settings.platform_base_url + '/api/agent/installer/openbas/windows')}).Content`;
    };
    const buildExclusionPath = (advanced: string, standard: string, other: string) => {
      let result = other;
      if (agentFolder) {
        return agentFolder; // If agentFolder is truthy, return it immediately
      }
      if (activeTab === 1 && selectedOption !== USER) {
        result = advanced;
      } else if (activeTab === 0) {
        result = standard;
      }
      return result;
    };

    switch (platform) {
      case WINDOWS:
        return {
          icon: <Powershell />,
          label: 'powershell',
          exclusions: `${buildExclusionPath('C:\\Program Files (x86)\\Filigran\\OBAS Agent', 'C:\\Filigran', 'C:\\Filigran')}

Caldera injector hashes:
MD5: 68c1795fb45cb9b522d6cf48443fdc37
SHA1: 5f87d06f818ff8cba9e11e8cd1c6f9d990eca0f8
SHA256: 6b180913acb8cdac3fb8d3154a2f6a0bed13c056a477f4f94c4679414ec13b9f
SHA512: 6185b7253eedfa6253f26cd85c4bcfaf05195219b6ab06b43d9b07279d7d0cdd3c957bd58d36058d7cde405bc8c5084f3ac060a6080bfc18a843738d3bee87fd`,
          displayedCode: buildUrlScript2Windows(),
          code: buildUrlScript2Windows(),
        };
      case LINUX:
        return {
          icon: <Bash />,
          label: 'sh',
          exclusions: `${buildExclusionPath('/opt/openbas-agent', '$HOME/.local/openbas-agent-session', '/opt/openbas-agent-service-USER')}
          
Caldera injector hashes:
MD5: d604c952bb3c6d96621594d39992c499
SHA1: 5b6087f87f5f2ae129f888bba799611836eb39a2
SHA256: 98d1e64445bbef46a36d4724699a386646de78881a1b6f2b346122c76d696c12
SHA512: ca07dc1d0a5297e29327e483f4f35dadb254d96a16a5c33da5ad048e6965a3863d621518a2be40f1a42226c68cbf5e779382a37ee5baa7dd7c538ec73ce059e8`,
          displayedCode: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openbas/linux')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
          code: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openbas/linux')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
        };
      case MACOS:
        return {
          icon: <TerminalOutlined />,
          label: 'sh',
          exclusions: `${buildExclusionPath('/opt/openbas-agent', '$HOME/.local/openbas-agent-session', '/opt/openbas-agent-service-USER')}
          
Caldera injector hashes:
MD5: 1132906cc40001f51673108847b88d0c
SHA1: 3177df4a8fa13a2e13ce63670c579955ad55df3f
SHA256: 2b4397160925bf6b9dcca0949073fd9b2fc590ab641ea1d1c3d7d36048ed674a
SHA512: f1c8cf0c41c7d193bcb2aad21d7a739c785902c3231e15986b2eb37f911824a802f50cb2dbb509deba1c7a2a535fb7b34cf100303c61a6087102948628133747`,
          displayedCode: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openbas/macos')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
          code: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openbas/macos')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
        };
      default:
        return {
          icon: <Bash />,
          label: 'sh',
          exclusions: `${buildExclusionPath('/opt/openbas-agent', '$HOME/.local/openbas-agent-session', '/opt/openbas-agent-service-USER')}

Caldera injector hashes:
MD5: d604c952bb3c6d96621594d39992c499
SHA1: 5b6087f87f5f2ae129f888bba799611836eb39a2
SHA256: 98d1e64445bbef46a36d4724699a386646de78881a1b6f2b346122c76d696c12
SHA512: ca07dc1d0a5297e29327e483f4f35dadb254d96a16a5c33da5ad048e6965a3863d621518a2be40f1a42226c68cbf5e779382a37ee5baa7dd7c538ec73ce059e8`,
          displayedCode: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openbas/linux')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
          code: `curl -s ${buildInstallationUrl(settings.platform_agent_url + '/api/agent/installer/openbas/linux')} ${buildExtraParams(' | sudo sh -s -- --user USER --group GROUP', '| sh', '| sudo sh')}`,
        };
    }
  };
  const buildArchitectureFormControl = () => {
    if (platform !== MACOS) return <></>;

    return (
      <FormControl style={{
        width: '100%',
        margin: theme.spacing(1, 0),
      }}
      >
        <InputLabel id="arch">{t('Architecture')}</InputLabel>
        <Select
          labelId="arch"
          value={arch}
          onChange={event => setArch(event.target.value ?? x86_64)}
          fullWidth
        >
          <MenuItem value="x86_64">{t(x86_64)}</MenuItem>
          <MenuItem value="arm64">{t('arm64')}</MenuItem>
        </Select>
      </FormControl>
    );
  };
  const stepOneInstallationTitle = () => {
    return (
      <Typography variant="h2" style={{ marginTop: theme.spacing(3) }}>
        {t('Step 1 - Install the agent')}
      </Typography>
    );
  };
  const buildStepTwoExclusions = () => {
    const exclusions = selectedExecutor?.executor_type === OPENBAS_CALDERA ? buildCalderaInstallerScript().exclusions : buildOpenBASInstallerScript().exclusions;
    return (
      <>
        <Typography variant="h2" style={{ marginTop: theme.spacing(2) }}>{t('Step 2 - Add antivirus exclusions')}</Typography>
        <p>
          {t('You will need to add proper antivirus exclusions for this agent (to ensure injects execution to work properly). It may not be necessary in the future but this is generally a good practice to ensure the agent will be always available.')}
        </p>
        <pre style={{ margin: theme.spacing(2, 0, 1) }}>{exclusions}</pre>
      </>
    );
  };
  const buildInstallationScriptsAndActionButtons = () => {
    const fileExtension = platform === WINDOWS ? 'ps1' : 'sh';
    const code = selectedExecutor?.executor_type === OPENBAS_CALDERA ? buildCalderaInstallerScript().code : buildOpenBASInstallerScript().code;
    const displayedCode = selectedExecutor?.executor_type === OPENBAS_CALDERA ? buildCalderaInstallerScript().displayedCode : buildOpenBASInstallerScript().displayedCode;

    const buildInstallationMessage = () => {
      let message = '';
      if (selectedExecutor?.executor_type === OPENBAS_AGENT) {
        if (activeTab === 0 && platform === WINDOWS) {
          message = t('Run the following PowerShell snippet in a standard prompt or download the .ps1 script.');
        } else if (activeTab === 0 && platform !== WINDOWS) {
          message = t('Run the following bash snippet in a terminal or download the .sh script.');
        } else if (activeTab !== 0 && platform === WINDOWS && selectedOption === USER) {
          message = `${t('To install, copy and paste the following PowerShell snippet into an elevated prompt or download the .ps1 script.')} ${t('It can be run as administrator or as a standard user depending on the user rights used in the script parameters.')}`;
        } else if (activeTab !== 0 && platform === WINDOWS && selectedOption !== USER) {
          message = `${t('To install, copy and paste the following PowerShell snippet into an elevated prompt or download the .ps1 script and run it with administrator privileges.')} ${t('Installing it as a system grants system-wide privileges.')}`;
        } else if (activeTab !== 0 && platform !== WINDOWS && selectedOption === USER) {
          message = `${t('To install, copy and paste the following bash snippet into a terminal with root privileges, or download the .sh script and run it as root.')} ${t('It can be run as administrator or as a standard user depending on the user rights used in the script parameters.')}`;
        } else if (activeTab !== 0 && platform !== WINDOWS && selectedOption !== USER) {
          message = `${t('To install, copy and paste the following bash snippet into a terminal with root privileges, or download the .sh script and run it as root.')} ${t(`Installing it as a system grants system-wide privileges.`)}`;
        }
      } else if (selectedExecutor?.executor_type !== OPENBAS_AGENT && platform === WINDOWS) {
        message = t('You can whether directly copy and paste the following Powershell snippet in an elevated prompt or download the .ps1 script (and execute it as an administrator).');
      } else if (selectedExecutor?.executor_type !== OPENBAS_AGENT && platform !== WINDOWS) {
        message = t('You can whether directly copy and paste the following bash snippet in a root console or download the .sh script (and execute it as root).');
      }
      return (
        <p>{message}</p>
      );
    };

    return (
      <>
        {buildInstallationMessage()}
        {selectedExecutor?.executor_type === OPENBAS_CALDERA && platform !== WINDOWS && (
          <Alert variant="outlined" severity="warning">
            {t('For the moment, the following snippet or script will not add the agent at boot. Please be sure to add it in rc.local or other files to make it persistent. We will release proper packages in the near future.')}
          </Alert>
        )}
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
            onClick={() => download(displayedCode, `openbas.${fileExtension}`, 'text/plain')}
          >
            {t('Download')}
          </Button>
        </div>
      </>
    );
  };
  const buildPlatformInstallationForCaldera = () => {
    return (
      <>
        {platform === WINDOWS && (buildInstallationScriptsAndActionButtons())}
        {platform !== WINDOWS && (buildInstallationScriptsAndActionButtons())}
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
          {stepOneInstallationTitle()}
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
        {stepOneInstallationTitle()}
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
          {/* Caldera */}
          {selectedExecutor.executor_type === OPENBAS_CALDERA && (
            <div>
              {buildArchitectureFormControl()}
              {stepOneInstallationTitle()}
              <Alert variant="outlined" severity="info">
                {t('Installing the agent is requiring local administrator privileges.')}
              </Alert>
              {buildPlatformInstallationForCaldera()}
              {buildStepTwoExclusions()}
            </div>
          )}

          {/* OBAS */}
          {selectedExecutor && selectedExecutor.executor_type === OPENBAS_AGENT && (
            <div>
              <Tabs value={activeTab} onChange={handleTabChange}>
                <Tab label={t('Standard Installation')} />
                <Tab label={t('Advanced Installation')} />
              </Tabs>
              {activeTab === 0 && (buildStandardInstallation())}
              {activeTab === 1 && (buildAdvancedInstallation())}
              {buildStepTwoExclusions()}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default InstructionSelector;
