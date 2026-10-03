package org.litvin

/**
 * The hook arguments of Velopack (B-24). The Velopack setup and the Velopack update start the app EXE
 * with one of these arguments, for example `--veloapp-install 1.4.0`. Velopack always runs the hooks.
 * A hook process must show no UI and must exit within 30 seconds (install, uninstall) or 15 seconds
 * (update). Otherwise Velopack kills it and shows a warning.
 *
 * The hooks (https://docs.velopack.io/integrating/hooks, checked on 2026-10-03) are
 * `--veloapp-install`, `--veloapp-obsolete`, `--veloapp-updated`, and `--veloapp-uninstall`. The app
 * treats each argument that starts with [PREFIX] as a hook, so a new hook of a later Velopack version
 * also exits.
 *
 * The first start after the install and a restart by Velopack are not hooks. Velopack gives them as
 * the environment variables `VELOPACK_FIRSTRUN` and `VELOPACK_RESTART`, with no argument. Thus, the
 * app starts as usual for them.
 */
object VelopackHooks {
    const val PREFIX = "--veloapp-"

    /** True when Velopack started this process for a hook. Then `main` must exit at once. */
    fun isHookProcess(args: Array<String>): Boolean = args.any { it.startsWith(PREFIX) }
}
