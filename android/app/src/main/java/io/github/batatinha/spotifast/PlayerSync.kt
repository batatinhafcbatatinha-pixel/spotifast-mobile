package io.github.batatinha.spotifast

/**
 * What the screen already shows after a tap, ahead of the player.
 *
 * The player takes a moment to act on a command (loading a song takes a
 * second or more), and a report read meanwhile still describes the moment
 * before the tap. Until the player agrees, or [until] passes, such reports
 * are held back, so what the user did never flickers away and comes back.
 */
sealed class Expectation(val until: Long) {
    abstract fun metBy(state: PlayerState): Boolean

    class Playing(val playing: Boolean, until: Long) : Expectation(until) {
        override fun metBy(state: PlayerState) = state.playing == playing
    }

    class Track(val uri: String, until: Long) : Expectation(until) {
        override fun metBy(state: PlayerState) = state.track?.uri == uri
    }

    /** Next was tapped: the song after isn't known here, only that this one goes. */
    class Leaves(val uri: String?, until: Long) : Expectation(until) {
        override fun metBy(state: PlayerState) = state.track?.uri != uri
    }
}

/** The state to show, and the expectation still waiting, once [reported] arrives. */
fun reconcile(shown: PlayerState, reported: PlayerState, expectation: Expectation?, now: Long): Pair<PlayerState, Expectation?> =
    when {
        expectation == null || now >= expectation.until || expectation.metBy(reported) -> reported to null
        // The player lost its connection: show that rather than the guess.
        !reported.connected && shown.connected -> reported to null
        else -> shown to expectation
    }

/** A song as the mini player shows it, before the player has loaded it. */
fun Song.nowPlaying() = NowPlaying(uri, name, artists, album, imageUrl, durationMs)
