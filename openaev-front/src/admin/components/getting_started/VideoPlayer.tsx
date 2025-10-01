import { useEffect, useRef } from 'react';

const iframeWrapperStyle: React.CSSProperties = {
  position: 'relative',
  width: '100%',
  aspectRatio: '16 / 9',
  transform: 'scale(1)',
};

const iframeStyle: React.CSSProperties = {
  position: 'absolute',
  width: '100%',
  height: '100%',
  border: '1px solid white',
  borderRadius: '12px',
};

interface VideoPlayerProps { videoLink: string }

const VideoPlayer = ({ videoLink }: VideoPlayerProps) => {
  const scriptLoadedRef = useRef(false);

  useEffect(() => {
    const scriptId = 'storylane-embed';
    if (!document.getElementById(scriptId)) {
      const script = document.createElement('script');
      script.src = 'https://js.storylane.io/js/v2/storylane.js';
      script.async = true;
      script.id = scriptId;
      document.body.appendChild(script);
      scriptLoadedRef.current = true;
    }
  }, []);

  return (
    <div className="sl-embed" style={iframeWrapperStyle}>
      <iframe
        loading="lazy"
        className="sl-demo"
        src={videoLink}
        name="sl-embed"
        allow="fullscreen"
        allowFullScreen
        style={iframeStyle}
        title="Storylane Demo"
      />
    </div>
  );
};

export default VideoPlayer;
