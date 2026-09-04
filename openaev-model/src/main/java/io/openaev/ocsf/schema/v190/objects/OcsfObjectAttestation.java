package io.openaev.ocsf.schema.v190.objects;

import io.openaev.ocsf.schema.OcsfObject;

@lombok.Getter
public class OcsfObjectAttestation extends OcsfObject {
  /**
   * Unique identifier of the authority that produced this attestation. When the attestation has a
   * signature, <code>authority_uid</code> ties the signing credential to a known party: because
   * signing credentials rotate and expire, a verifier uses this identifier to confirm that the
   * credential belongs to the expected authority, rather than to a different holder of some
   * otherwise-valid credential. Where multiple <code>signatures</code> are present, it identifies
   * the authority that produced the attestation; the identity of each co-signer is carried within
   * its own <code>digital_signature</code> object. Included in the canonical serialization.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "authority_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT authorityUidField;

  /**
   * Identifier of the append-only chain, such as a forensic or audit log, that this event belongs
   * to. It groups a sequence of attestations so that an independent verifier can locate and
   * validate them in order; it identifies the chain itself and is not a reference to any event
   * outside of this one. Stable for the lifetime of the chain. For example, every attestation
   * produced during a single agent session shares one <code>chain_uid</code>, so querying events
   * whose <code>attestation.chain_uid</code> matches retrieves the full chain, including its newest
   * entry, while each event links to its predecessor through <code>prev_event</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "chain_uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT chainUidField;

  /**
   * The fingerprint of this event's canonical serialization. If <code>signatures</code> are
   * present, each signature is computed over this fingerprint. Without signatures, the fingerprint
   * alone still detects accidental alteration or corruption of the event. The next event in a chain
   * references this value through its own <code>prev_event.fingerprint</code>.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "fingerprint")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectFingerprint fingerprintField;

  /**
   * Reference to the previous event in a tamper-evident chain, carrying that event's fingerprint
   * together with locator attributes for retrieval. Absent on the first, or genesis, event of a
   * chain.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "prev_event")
  private io.openaev.ocsf.schema.v190.objects.OcsfObjectPrevEvent prevEventField;

  /**
   * One or more digital signatures, each computed over this event's <code>fingerprint</code> and
   * thereby over the event's canonical serialization, each bound to a verifiable identity. The
   * first entry is typically the producer; additional entries carry co-signatures such as a notary
   * or witness over the same event. The signing algorithm, certificate or public-key material, and
   * signing time are carried within each <code>digital_signature</code> object.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "signatures")
  private java.util.List<io.openaev.ocsf.schema.v190.objects.OcsfObjectDigitalSignature>
      signaturesField;

  /**
   * Unique identifier of this attestation. It distinguishes an individual attestation, such as a
   * single entry within a tamper-evident chain, from the chain as a whole. See <code>chain_uid
   * </code> for the identifier of the chain itself.
   */
  @com.fasterxml.jackson.annotation.JsonProperty(value = "uid")
  private io.openaev.ocsf.schema.v190.datatypes.OcsfDatatypeStringT uidField;
}
