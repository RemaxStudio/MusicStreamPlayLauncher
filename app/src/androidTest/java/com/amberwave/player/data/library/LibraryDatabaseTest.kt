package com.amberwave.player.data.library

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amberwave.player.feature.collection.CollectionSource
import com.amberwave.player.feature.collection.LibraryTrack
import com.amberwave.player.feature.collection.RoomLibraryRepository
import com.amberwave.catalog.model.CatalogArtistRef
import com.amberwave.catalog.model.CatalogPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: LibraryDatabase? = null

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LibraryDatabase::class.java,
    )

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun playlistRelationsAndFavoritesRemainIndependent() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val repository = RoomLibraryRepository(database!!.libraryDao()) { 100L }
        val playlistId = repository.createPlaylist("Focus").getOrThrow()
        val track = track("audius:1")

        repository.addTrackToPlaylist(playlistId, track)
        repository.setFavorite(track, true)
        val storedTrack = repository.observeTracks(CollectionSource.Playlist(playlistId)).first().single()
        assertEquals(track.id, storedTrack.id)
        assertEquals(track.artistRef, storedTrack.artistRef)

        repository.deletePlaylist(playlistId)
        assertTrue(repository.observePlaylists().first().isEmpty())
        assertEquals(
            listOf(track.id),
            repository.observeTracks(CollectionSource.Favorites).first().map(LibraryTrack::id),
        )
    }

    @Test
    fun freshSchemaContainsEveryLibraryTable() {
        migrationHelper.createDatabase(TEST_DATABASE, LibraryDatabase.SCHEMA_VERSION).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                val tables = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertTrue(tables.containsAll(listOf("library_tracks", "playlists", "recent_tracks")))
            }
        }
    }

    @Test
    fun recentPlaybackIsUniqueOrderedAndCappedAtFifty() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        var clock = 0L
        val repository = RoomLibraryRepository(database!!.libraryDao()) { ++clock }

        repeat(52) { index -> repository.recordRecentlyPlayed(track("AUDIUS:$index")) }
        val capped = repository.observeRecentlyPlayed().first()
        assertEquals(50, capped.size)
        assertEquals("AUDIUS:51", capped.first().id)
        assertEquals("AUDIUS:2", capped.last().id)

        repository.recordRecentlyPlayed(track("AUDIUS:10"))
        val reordered = repository.observeRecentlyPlayed().first()
        assertEquals(50, reordered.size)
        assertEquals("AUDIUS:10", reordered.first().id)
        assertEquals(1, reordered.count { it.id == "AUDIUS:10" })
    }

    private fun track(id: String) = LibraryTrack(
        id = id,
        title = "Track",
        artist = "Artist",
        artworkUrl = null,
        artworkThumbnailUrls = emptyList(),
        artworkFallbackRes = 0,
        streamUrl = "https://example.test/track.mp3",
        durationMs = 60_000,
        lyrics = null,
        description = null,
        artistRef = CatalogArtistRef("artist-1", CatalogPlatform.AUDIUS, "Artist"),
    )

    private companion object {
        const val TEST_DATABASE = "music-library-migration-test"
    }
}
