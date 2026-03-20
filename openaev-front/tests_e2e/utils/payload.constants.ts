import type PayloadFormComponent from '../model/payloads/PayloadFormComponent';

export const PayloadCommandTypes = {
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

export type PayloadFormFields = keyof Omit<PayloadFormComponent, 'page'>;

export const GeneralTabFields = {
  requiredFields: ['nameField'],
  optionalFields: ['descriptionField', 'attackPatternsField', 'tagsField', 'expectationsField'],
};

export const CommandTypeFields = {
  common: ['typeField', 'architectureField', 'platformsField', 'argumentBtn', 'prerequisiteBtn'],
  [PayloadCommandTypes.COMMAND_LINE]: ['executorField', 'commandField'],
  [PayloadCommandTypes.EXECUTABLE]: ['documentsAddBtn'],
  [PayloadCommandTypes.FILE_DROP]: ['documentsAddBtn'],
  [PayloadCommandTypes.DNS_RESOLUTION]: ['hostnameField'],
} as const;

export const ArchitectureConfigs = [
  {
    commandType: PayloadCommandTypes.COMMAND_LINE,
    expectedOptions: [Architectures.X86_64, Architectures.ARM64, Architectures.ALL],
  },
  {
    commandType: PayloadCommandTypes.EXECUTABLE,
    expectedOptions: [Architectures.X86_64, Architectures.ARM64],
  },
  {
    commandType: PayloadCommandTypes.FILE_DROP,
    defaultValue: Architectures.ALL,
  },
  {
    commandType: PayloadCommandTypes.DNS_RESOLUTION,
    defaultValue: Architectures.ALL,
  },
];
