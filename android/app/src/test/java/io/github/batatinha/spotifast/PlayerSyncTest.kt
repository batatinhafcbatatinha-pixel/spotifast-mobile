package io.github.batatinha.spotifast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlayerSyncTest {
    private fun track(uri: String) = NowPlaying(uri, uri, "", "", null, 1000)
    private val before = PlayerState(connected = true, playing = true, track = track("a"))

    @Test
    fun a_tapped_song_stays_while_the_player_still_reports_the_old_one() {
        val shown = before.copy(track = track("b"), loading = true)
        val expect = Expectation.Track("b", until = 10_000)
        val (state, waiting) = reconcile(shown, before, expect, now = 500)
        assertSame(shown, state)
        assertSame(expect, waiting)
    }

    @Test
    fun the_players_report_takes_over_once_it_agrees() {
        val shown = before.copy(track = track("b"), loading = true)
        val reported = before.copy(track = track("b"), playing = true)
        val (state, waiting) = reconcile(shown, reported, Expectation.Track("b", 10_000), now = 900)
        assertEquals(reported, state)
        assertNull(waiting)
    }

    @Test
    fun pause_shows_at_once_and_a_stale_playing_report_does_not_undo_it() {
        val shown = before.copy(playing = false)
        val expect = Expectation.Playing(false, until = 3_000)
        assertSame(shown, reconcile(shown, before, expect, now = 100).first)
        assertEquals(before.copy(playing = false), reconcile(shown, before.copy(playing = false), expect, 200).first)
    }

    @Test
    fun a_player_that_never_agrees_is_believed_after_the_wait() {
        val shown = before.copy(playing = false)
        val (state, waiting) = reconcile(shown, before, Expectation.Playing(false, until = 3_000), now = 3_000)
        assertEquals(before, state)
        assertNull(waiting)
    }

    @Test
    fun a_lost_connection_shows_rather_than_the_guess() {
        val shown = before.copy(track = track("b"))
        val lost = PlayerState(connected = false)
        assertEquals(lost, reconcile(shown, lost, Expectation.Track("b", 10_000), now = 100).first)
    }

    @Test
    fun without_an_expectation_every_report_shows() {
        val reported = before.copy(positionMs = 42)
        assertEquals(reported, reconcile(before, reported, null, now = 0).first)
    }
}

class NextTest {
    private fun track(uri: String) = NowPlaying(uri, uri, "", "", null, 1000)

    @Test
    fun next_keeps_its_spinner_until_another_song_is_reported() {
        val before = PlayerState(connected = true, playing = true, track = track("a"))
        val shown = before.copy(loading = true)
        val expect = Expectation.Leaves("a", until = 4_000)
        assertSame(shown, reconcile(shown, before, expect, now = 300).first)
        val after = before.copy(track = track("b"))
        assertEquals(after, reconcile(shown, after, expect, now = 900).first)
    }
}
