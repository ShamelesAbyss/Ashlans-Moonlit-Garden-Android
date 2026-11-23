package com.shamelesabyss.moonlitgarden.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject

data class LocalTrack(val id: String, val title: String, val artist: String, val assetPath: String)
data class MusicUiState(
    val tracks: List<LocalTrack> = emptyList(),
    val selectedIndex: Int = 0,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatOne: Boolean = false,
    val volume: Float = 0.25f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val notice: String = "",
) {
    val selectedTrack: LocalTrack? get() = tracks.getOrNull(selectedIndex)
}

/** Local-only ExoPlayer. The bundled manifest intentionally has no audio until distribution rights are cleared. */
class MusicController(context: Context) {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(appContext).build()
    var uiState by mutableStateOf(loadManifestState())
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                uiState = uiState.copy(isPlaying = isPlaying)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                uiState = uiState.copy(selectedIndex = player.currentMediaItemIndex.coerceAtLeast(0), positionMs = 0L)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePosition()
            }
        })
        val tracks = uiState.tracks
        if (tracks.isNotEmpty()) {
            player.setMediaItems(tracks.map { MediaItem.fromUri("asset:///${it.assetPath}") })
            player.prepare()
        }
    }

    fun togglePlayback() {
        if (uiState.tracks.isEmpty()) {
            uiState = uiState.copy(notice = "The original track is preserved in the private reference project. Public offline playback is paused until its distribution rights are confirmed.")
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun previous() { if (uiState.tracks.isNotEmpty()) player.seekToPreviousMediaItem() }
    fun next() { if (uiState.tracks.isNotEmpty()) player.seekToNextMediaItem() }

    fun choose(index: Int) {
        if (index !in uiState.tracks.indices) return
        player.seekTo(index, 0L)
        uiState = uiState.copy(selectedIndex = index, positionMs = 0L)
    }

    fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        uiState = uiState.copy(shuffle = enabled)
    }

    fun toggleRepeat() {
        val one = !uiState.repeatOne
        player.repeatMode = if (one) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        uiState = uiState.copy(repeatOne = one)
    }

    fun setVolume(volume: Float) {
        val value = volume.coerceIn(0f, 1f)
        player.volume = value
        uiState = uiState.copy(volume = value)
    }

    fun seek(positionMs: Long) {
        if (uiState.tracks.isNotEmpty()) player.seekTo(positionMs.coerceAtLeast(0L))
        updatePosition()
    }

    fun updatePosition() {
        if (uiState.tracks.isEmpty()) return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        uiState = uiState.copy(positionMs = player.currentPosition.coerceAtLeast(0L), durationMs = duration)
    }

    fun pauseForBackground() = player.pause()
    fun release() = player.release()

    private fun loadManifestState(): MusicUiState {
        return try {
            val root = JSONObject(appContext.assets.open("soundtrack/manifest.json").bufferedReader().use { it.readText() })
            val tracksJson = root.optJSONArray("tracks") ?: return MusicUiState()
            val tracks = (0 until tracksJson.length()).mapNotNull { index ->
                val item = tracksJson.optJSONObject(index) ?: return@mapNotNull null
                val path = item.optString("asset", "").removePrefix("/")
                if (path.isBlank() || path.contains("..")) return@mapNotNull null
                LocalTrack(
                    id = item.optString("id", "track-$index"),
                    title = item.optString("title", "Untitled track"),
                    artist = item.optString("artist", "Unknown artist"),
                    assetPath = path,
                )
            }
            MusicUiState(tracks = tracks)
        } catch (_: Exception) {
            MusicUiState(notice = "The local soundtrack manifest could not be read.")
        }
    }
}
