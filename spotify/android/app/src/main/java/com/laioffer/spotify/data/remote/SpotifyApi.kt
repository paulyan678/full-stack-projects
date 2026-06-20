package com.laioffer.spotify.data.remote

import com.laioffer.spotify.data.model.Playlist
import com.laioffer.spotify.data.model.Section
import retrofit2.http.GET
import retrofit2.http.Path

interface SpotifyApi {
    @GET("feed")
    suspend fun getHomeFeed(): List<Section>

    @GET("playlists")
    suspend fun getPlaylists(): List<Playlist>

    @GET("playlist/{id}")
    suspend fun getPlaylist(@Path("id") id: Int): Playlist
}
