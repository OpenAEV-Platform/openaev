import type ThreatArsenalFormComponent from '../model/threat-arsenals/ThreatArsenalFormComponent';

export const ThreatArsenalCommandTypes = {
  COMMAND_LINE: 'Command Line',
  EXECUTABLE: 'Executable',
  FILE_DROP: 'File Drop',
  DNS_RESOLUTION: 'DNS Resolution',
} as const;

export const Architectures = {
  X86_64: 'x86_64',
  ARM64: 'arm64',
  ALL: 'All architectures',
} as const;

export type ThreatArsenalFormFields = keyof Omit<ThreatArsenalFormComponent, 'page'>;

export const GeneralTabFields = {
  requiredFields: ['nameField'],
  optionalFields: ['descriptionField', 'attackPatternsField', 'tagsField', 'expectationsField'],
};

export const CommandTypeFields: Record<string, readonly string[]> = {
  common: ['typeField', 'architectureField', 'platformsField', 'argumentBtn', 'prerequisiteBtn'],
  [ThreatArsenalCommandTypes.COMMAND_LINE]: ['executorField', 'commandField'],
  [ThreatArsenalCommandTypes.EXECUTABLE]: ['documentsAddBtn'],
  [ThreatArsenalCommandTypes.FILE_DROP]: ['documentsAddBtn'],
  [ThreatArsenalCommandTypes.DNS_RESOLUTION]: ['hostnameField'],
} as const;

export const ArchitectureConfigs = [
  {
    commandType: ThreatArsenalCommandTypes.COMMAND_LINE,
    expectedOptions: [Architectures.X86_64, Architectures.ARM64, Architectures.ALL],
  },
  {
    commandType: ThreatArsenalCommandTypes.EXECUTABLE,
    expectedOptions: [Architectures.X86_64, Architectures.ARM64],
  },
  {
    commandType: ThreatArsenalCommandTypes.FILE_DROP,
    defaultValue: Architectures.ALL,
  },
  {
    commandType: ThreatArsenalCommandTypes.DNS_RESOLUTION,
    defaultValue: Architectures.ALL,
  },
];
