export const stringToColour = (str: string | null | undefined, reversed = false): string => {
  if (!str) {
    return '#5d4037';
  }
  if (str === 'true') {
    if (reversed) {
      return '#bf360c';
    }
    return '#2e7d32';
  }
  if (str === 'false') {
    if (reversed) {
      return '#2e7d32';
    }
    return '#bf360c';
  }
  let hash = 0;
  for (let i = 0; i < str.length; i += 1) {
    // eslint-disable-next-line no-bitwise
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  let colour = '#';
  for (let i = 0; i < 3; i += 1) {
    // eslint-disable-next-line no-bitwise
    const value = (hash >> (i * 8)) & 0xff;
    colour += `00${value.toString(16)}`.slice(-2);
  }
  return colour;
};

export const hexToRGB = (hex: string, transp = 0.1): string => {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${transp})`;
};

export interface SeverityAndColor {
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
  color: string;
}

// CVSS
export const getSeverityAndColor = (score: number | string | null | undefined): SeverityAndColor => {
  const numScore = typeof score === 'string' ? parseFloat(score) : (score ?? 0);

  if (numScore >= 9.0) {
    return {
      severity: 'CRITICAL',
      color: 'red',
    };
  }
  if (numScore >= 7.0) {
    return {
      severity: 'HIGH',
      color: 'orangered',
    };
  }
  if (numScore >= 4.0) {
    return {
      severity: 'MEDIUM',
      color: 'orange',
    };
  }
  if (numScore > 0.0) {
    return {
      severity: 'LOW',
      color: 'green',
    };
  }
  return {
    severity: 'NONE',
    color: 'gray',
  };
};
