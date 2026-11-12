import { describe, expect, it } from 'vitest';

import { FDS } from '../../components/fds-tokens.generated';
import ThemeDark from '../../components/ThemeDark';
import ThemeLight from '../../components/ThemeLight';

interface AiTones {
  main: string;
  light: string;
  dark: string;
}

interface PaletteWithAi {
  ai: AiTones;
  designSystem: { ia: AiTones };
}

const paletteOf = (options: ReturnType<typeof ThemeDark>) => options.palette as unknown as PaletteWithAi;

// Ask Ariane and every AI surface read palette.ai: it must be the design system's
// IA token in both modes, never a retyped literal (fds-migration/TOKEN-MAPPING.md § 8.1).
describe('AI accent of the OpenAEV themes', () => {
  it('given_theDarkTheme_should_paintTheAiAccentWithTheDesignSystemIaTokens', () => {
    // Act
    const palette = paletteOf(ThemeDark());

    // Assert
    expect(palette.ai.main).toBe(FDS.colors.dark['--color-filigran-ia-primary']);
    expect(palette.ai.light).toBe(FDS.colors.dark['--color-filigran-ia-secondary']);
    expect(palette.ai.dark).toBe(FDS.colors.dark['--color-filigran-ia-tertiary']);
    expect(palette.designSystem.ia).toEqual({
      main: palette.ai.main,
      light: palette.ai.light,
      dark: palette.ai.dark,
    });
  });

  it('given_theLightTheme_should_paintTheAiAccentWithTheDesignSystemIaTokens', () => {
    // Act
    const palette = paletteOf(ThemeLight());

    // Assert
    expect(palette.ai.main).toBe(FDS.colors.light['--color-filigran-ia-primary']);
    expect(palette.ai.light).toBe(FDS.colors.light['--color-filigran-ia-tertiary']);
    expect(palette.ai.dark).toBe(FDS.colors.light['--color-filigran-ia-secondary']);
    expect(palette.designSystem.ia).toEqual({
      main: palette.ai.main,
      light: palette.ai.light,
      dark: palette.ai.dark,
    });
  });
});
