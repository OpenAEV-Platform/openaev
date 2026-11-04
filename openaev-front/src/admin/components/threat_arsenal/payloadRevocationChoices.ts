import { type PayloadVersionOutput } from '../../../utils/api-types';

// Versions a revocation can take: the live ones, or for lifting the revoked ones.
const payloadRevocationChoices = (versions: PayloadVersionOutput[], lift: boolean, customized: boolean) => versions
  .filter(v => v.payload_id && v.payload_installed
    && (lift ? v.payload_revoked_in_tenant || (customized && v.payload_revoked) : !v.payload_revoked));

export default payloadRevocationChoices;
