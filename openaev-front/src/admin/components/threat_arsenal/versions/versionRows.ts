import { type CatalogPayloadOutput, type PayloadVersionOutput } from '../../../../utils/api-types';

export interface VersionRow {
  version: string;
  // Empty for a catalogue version not installed in the tenant.
  installed?: PayloadVersionOutput;
}

/** The installed versions and the catalogue ones not installed, newest first. */
export const versionRows = (installed: PayloadVersionOutput[], notInstalled: string[] = []): VersionRow[] => [
  ...installed.map(version => ({
    version: version.payload_version,
    installed: version,
  })),
  ...notInstalled.map(version => ({ version })),
].sort((a, b) => b.version.localeCompare(a.version, undefined, { numeric: true }));

/** The catalogue versions of a payload not installed in the tenant. */
export const notInstalledVersions = (catalogue: CatalogPayloadOutput[], installed: PayloadVersionOutput[]) => catalogue
  .map(item => item.catalog_payload_version)
  .filter(version => !installed.some(row => row.payload_version === version));

/** The select value of a row: its payload id once installed, else its version. */
export const rowValue = (row: VersionRow) => row.installed?.payload_id ?? row.version;
