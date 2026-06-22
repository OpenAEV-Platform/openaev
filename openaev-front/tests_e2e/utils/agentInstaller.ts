import os from 'node:os';

export type AgentInstallExecution = {
  command: string;
  shell?: string;
};

/**
 * Normalizes installer commands copied from the UI so they are reliable on CI runners.
 */
export const normalizeAgentInstallCommand = (rawCommand: string): AgentInstallExecution => {
  let command = rawCommand.trim();
  let shell: string | undefined;

  if (os.platform() === 'win32') {
    // PowerShell 5.1 can prompt on iwr unless -UseBasicParsing is set.
    command = command.replace(/\b(iwr|Invoke-WebRequest)\b/, '$1 -UseBasicParsing');
    shell = 'powershell';
  } else {
    // Keep the backend-provided command, but avoid piping into sh (dash in CI).
    command = command.replace(/\|\s*sh\b/, '| bash');
    shell = '/bin/bash';
  }

  return {
    command,
    shell,
  };
};
