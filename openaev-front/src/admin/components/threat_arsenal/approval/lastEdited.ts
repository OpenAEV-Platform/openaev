import { type PayloadVersionOutput } from '../../../../utils/api-types';

/**
 * Who last saved a version and when: the person an approver deals with, not its first author. A
 * version fixed and sent again shows who fixed it. When no one is recorded (platform content,
 * payloads from before the upgrade), the name is undefined and screens show "Unknown".
 */
export const lastEdited = (version: PayloadVersionOutput) => ({
  name: version.payload_last_modified_by_name ?? undefined,
  at: version.payload_last_edited_at ?? version.payload_created_at ?? undefined,
});
