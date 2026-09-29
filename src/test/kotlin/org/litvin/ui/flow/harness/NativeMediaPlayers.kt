package org.litvin.ui.flow.harness

import org.litvin.media.MediaPlayerFactory
import org.litvin.media.MediaScreen
import org.litvin.media.PreviewMediaPlayerFactory
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import java.awt.Component
import java.awt.geom.Rectangle2D
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The production libmpv preview players. The factory keeps each player with its screen,
 * so that a native UI flow can read the mpv properties of the player.
 */
class NativeMediaPlayers : MediaPlayerFactory {
    class Player internal constructor(
        val screen: MediaScreen,
        private val adapter: MpvSwingMediaPlayerAdapter,
    ) {
        /** The component that shows the mpv video. */
        val component: Component get() = adapter.component

        /** The area of [component] that shows the video. Null before mpv reports it. */
        fun videoBounds(): Rectangle2D.Double? = adapter.videoBounds()

        /** Reads an mpv property. Returns null when mpv has no value or the player is closed. */
        fun property(name: String): String? = adapter.debugProperty(name)
    }

    private val lock = Any()
    private var lastCreated: MpvSwingMediaPlayerAdapter? = null
    private val delegate = PreviewMediaPlayerFactory(
        playerCreator = { MpvSwingMediaPlayerAdapter().also { lastCreated = it } },
    )
    private val created = CopyOnWriteArrayList<Player>()

    val players: List<Player> get() = created.toList()

    /** Returns the last player of [screen]. */
    fun player(screen: MediaScreen): Player =
        created.lastOrNull { it.screen == screen } ?: throw AssertionError("No $screen media player was created")

    override fun create(screen: MediaScreen): SwingMediaPlayer = synchronized(lock) {
        val managed = delegate.create(screen)
        created += Player(screen, checkNotNull(lastCreated))
        lastCreated = null
        managed
    }

    override fun close() = delegate.close()
}
