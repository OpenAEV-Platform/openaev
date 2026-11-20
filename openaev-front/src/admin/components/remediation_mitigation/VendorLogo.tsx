import { type FunctionComponent, useState } from 'react';

import crowdstrike from '../../../static/images/vendors/crowdstrike.png';
import microsoftDefender from '../../../static/images/vendors/microsoft_defender.png';
import microsoftSentinel from '../../../static/images/vendors/microsoft_sentinel.png';
import paloAltoCortex from '../../../static/images/vendors/palo_alto_cortex.png';
import sentinelone from '../../../static/images/vendors/sentinelone.png';
import splunk from '../../../static/images/vendors/splunk.png';

// PROTOTYPE: the product resolves these from the security platform / collector image endpoints
// (see useExpectationSourceLogo). The prototype has no backend, so the logos are bundled. A vendor
// without a bundled logo (Elastic) falls back to its initial.
const VENDOR_LOGOS: Record<string, string> = {
  crowdstrike,
  microsoft_defender: microsoftDefender,
  microsoft_sentinel: microsoftSentinel,
  palo_alto_cortex: paloAltoCortex,
  sentinelone,
  splunk,
  splunk_es: splunk,
};

interface Props {
  vendorId: string;
  label: string;
  size?: number;
}

const VendorLogo: FunctionComponent<Props> = ({ vendorId, label, size = 18 }) => {
  const [failed, setFailed] = useState(false);
  const src = VENDOR_LOGOS[vendorId];

  if (src && !failed) {
    return (
      <img
        src={src}
        alt=""
        aria-hidden
        onError={() => setFailed(true)}
        style={{
          width: size,
          height: size,
          borderRadius: 4,
          objectFit: 'contain',
          flexShrink: 0,
        }}
      />
    );
  }
  return (
    <span
      aria-hidden
      style={{
        width: size,
        height: size,
        borderRadius: 4,
        flexShrink: 0,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: size * 0.6,
        fontWeight: 700,
        background: 'color-mix(in srgb, currentColor 15%, transparent)',
      }}
    >
      {label.charAt(0).toUpperCase()}
    </span>
  );
};

export default VendorLogo;
