package org.libsdl.app;

/**
 * hypseus links SDL3 (and everything else - Vorbis, libzip, libmpeg2) as a
 * static library into a single libmain.so, rather than the stock SDL
 * template's assumption of a separate libSDL3.so alongside libmain.so.
 * SDLActivity's default getLibraries() tries to load "SDL3" first, which
 * doesn't exist here and throws UnsatisfiedLinkError before "main" is ever
 * reached - overriding it to just "main" is SDLActivity's documented
 * customization point for exactly this case.
 */
public class HypseusActivity extends SDLActivity {
    public static final String EXTRA_ARGS = "org.libsdl.app.HypseusActivity.EXTRA_ARGS";

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "main"
        };
    }

    /**
     * MainActivity constructs the real argv (see LaunchArgs.kt) and passes
     * it via this Intent extra before starting this activity - the base
     * SDLActivity default (empty array) only gets hypseus as far as its own
     * "no game specified" non-crashing exit path (verified in Phase C).
     */
    @Override
    protected String[] getArguments() {
        String[] args = getIntent().getStringArrayExtra(EXTRA_ARGS);
        return args != null ? args : new String[0];
    }
}
