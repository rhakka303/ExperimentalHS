/* Phase A toolchain smoke test — not the real Hypdroid app.
 * Proves NDK + Gradle + SDL3's JNI glue work end to end before
 * bringing hypseus's own C++ codebase into the mix (Phase C). */
#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>

int main(int argc, char *argv[]) {
    (void)argc;
    (void)argv;

    SDL_Log("HYPDROID_PHASE_A_SMOKE_TEST: starting");

    if (!SDL_Init(SDL_INIT_EVENTS | SDL_INIT_VIDEO)) {
        SDL_LogError(SDL_LOG_CATEGORY_APPLICATION, "SDL_Init failed (%s)", SDL_GetError());
        return 1;
    }

    SDL_Log("HYPDROID_PHASE_A_SMOKE_TEST: SDL_Init succeeded");

    if (!SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_INFORMATION, "Hypdroid Phase A",
                                 "NDK + Gradle + SDL3 toolchain works.", NULL)) {
        SDL_LogError(SDL_LOG_CATEGORY_APPLICATION, "SDL_ShowSimpleMessageBox failed (%s)", SDL_GetError());
        return 1;
    }

    SDL_Log("HYPDROID_PHASE_A_SMOKE_TEST: message box shown, quitting");

    SDL_Quit();
    return 0;
}
