// Turns MANAGE_ASSESSMENT into "Manage assessment", for a capability without a translated label.
const humanizeCapability = (capability: string): string => {
  const lowered = capability.replace(/_/g, ' ').toLowerCase();
  return lowered.charAt(0).toUpperCase() + lowered.slice(1);
};

/** A capability's label, the same as in the role form (e.g. "Approve content"). */
const capabilityLabel = (t: (key: string) => string, capability: string): string => {
  const translated = t(capability);
  return translated === capability ? humanizeCapability(capability) : translated;
};

export default capabilityLabel;
