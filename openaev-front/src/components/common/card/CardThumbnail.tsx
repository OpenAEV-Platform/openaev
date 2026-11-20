import { Thumbnail } from '@filigran/design-system';
import { type ReactNode } from 'react';

interface Props {
    /** A logo: it fills the 48px thumbnail. Wins over `icon`. */
    image?: {
        src: string;
        alt: string;
    };
    /** An icon glyph, centered at 24px in the 48px thumbnail. */
    icon?: ReactNode;
}

// The leading slot of a library CardHeader, shared by the cards views: a logo
// fills the library Thumbnail, an icon sits centered in it. The frame stands
// one layer above the card, as the Figma Card_Header draws it.
const CardThumbnail = ({ image, icon }: Props) => (
    <Thumbnail elevation={2}>
        {image
            ? (
                <img
                    src={image.src}
                    alt={image.alt}
                    style={{
                        width: '100%',
                        height: '100%',
                        objectFit: 'contain',
                    }}
                />
            )
            : icon}
    </Thumbnail>
);

export default CardThumbnail;