package com.laioffer.spotify.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.laioffer.spotify.BuildConfig
import com.laioffer.spotify.data.local.FavoriteAlbumDao
import com.laioffer.spotify.data.local.SpotifyDatabase
import com.laioffer.spotify.data.remote.SpotifyApi
import com.laioffer.spotify.data.repository.FavoritesRepository
import com.laioffer.spotify.data.repository.FeedRepository
import com.laioffer.spotify.data.repository.NetworkFeedRepository
import com.laioffer.spotify.data.repository.NetworkPlaylistRepository
import com.laioffer.spotify.data.repository.PlaylistRepository
import com.laioffer.spotify.data.repository.RoomFavoritesRepository
import com.laioffer.spotify.player.Media3PlaybackController
import com.laioffer.spotify.player.PlaybackController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindFeedRepository(implementation: NetworkFeedRepository): FeedRepository
    @Binds abstract fun bindPlaylistRepository(implementation: NetworkPlaylistRepository): PlaylistRepository
    @Binds abstract fun bindFavoritesRepository(implementation: RoomFavoritesRepository): FavoritesRepository
    @Binds abstract fun bindPlaybackController(implementation: Media3PlaybackController): PlaybackController
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            },
        )
        .build()

    @Provides
    @Singleton
    fun provideSpotifyApi(client: OkHttpClient): SpotifyApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SpotifyDatabase =
        Room.databaseBuilder(context, SpotifyDatabase::class.java, "spotify-local.db").build()

    @Provides
    fun provideFavoriteAlbumDao(database: SpotifyDatabase): FavoriteAlbumDao = database.favoriteAlbumDao()

    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer = ExoPlayer.Builder(context).build()
}
