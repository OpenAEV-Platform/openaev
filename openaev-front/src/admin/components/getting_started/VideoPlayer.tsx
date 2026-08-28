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
  // Design-system frame: standard paper border tone and 4px radius, instead of
  // the former bright white 12px-rounded outline.
  border: '1px solid rgba(255, 255, 255, 0.12)',
  borderRadius: '4px',
};

interface VideoPlayerProps { videoLink: string }

const VideoPlayer = ({ videoLink }: VideoPlayerProps) => {
  const scriptLoadedRef = useRef(false);

  useEffect(() => {
    const scriptId = 'oaev-demo-embed';
    if (!document.getElementById(scriptId)) {
      const script = document.createElement('script');
      script.src = videoLink;
      script.async = true;
      script.id = scriptId;
      document.body.appendChild(script);
      scriptLoadedRef.current = true;
    }
  }, []);

  return (
    <div className="oaev-demo-embed" style={iframeWrapperStyle}>
      <iframe
        loading="lazy"
        className="oaev-demo"
        src={videoLink}
        name="oaev-demo-embed"
        allow="fullscreen"
        allowFullScreen
        style={iframeStyle}
        title="Video OpenAEV Demo"
      />
    </div>
  );
};

export default VideoPlayer;
