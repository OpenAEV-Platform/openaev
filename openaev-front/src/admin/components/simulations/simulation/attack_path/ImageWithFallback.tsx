import { type CSSProperties, type ReactNode, useState } from 'react';

interface Props {
  src: string;
  alt: string;
  // Rendered instead of the image when it fails to load (e.g. a collector/injector not installed in
  // dev 404s its logo).
  fallback: ReactNode;
  width?: number;
  height?: number;
  style?: CSSProperties;
}

// A small image that swaps in a fallback node on load error, instead of showing a broken image.
// Shared by the injector node and the security-platform logo, which both need the same behaviour.
const ImageWithFallback = ({ src, alt, fallback, width, height, style }: Props) => {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return <>{fallback}</>;
  }
  return (
    <img
      src={src}
      alt={alt}
      width={width}
      height={height}
      onError={() => setFailed(true)}
      style={style}
    />
  );
};

export default ImageWithFallback;
