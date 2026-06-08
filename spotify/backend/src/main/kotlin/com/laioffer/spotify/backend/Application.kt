package com.laioffer.spotify.backend

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.request.header
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import org.slf4j.event.Level

fun main() {
    val port = parsePort(System.getenv("PORT"))
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

internal fun parsePort(value: String?): Int {
    if (value.isNullOrBlank()) return 8080
    val port = value.toIntOrNull()
    require(port != null && port in 1..65_535) { "PORT must be an integer from 1 to 65535" }
    return port
}

fun Application.module() = spotifyModule(FixtureCatalog.load())

fun Application.spotifyModule(catalog: FixtureCatalog) {
    val appLog = environment.log
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }
    install(PartialContent)
    install(DefaultHeaders) {
        header(HttpHeaders.Server, "Spotify Local")
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "no-referrer")
        header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
    }
    install(CallLogging) {
        level = Level.INFO
    }
    install(StatusPages) {
        exception<CancellationException> { _, cause -> throw cause }
        exception<Throwable> { call, cause ->
            appLog.error("Unhandled API error", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Internal server error"))
        }
    }

    routing {
        get("/") {
            call.respondText("Spotify local API")
        }
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    albums = catalog.feed.sumOf { it.albums.size },
                    playlists = catalog.playlists.size,
                ),
            )
        }
        get("/feed") {
            call.respond(catalog.feed)
        }
        get("/playlists") {
            call.respond(catalog.playlists)
        }
        get("/playlist/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Playlist id must be an integer"))
            val playlist = catalog.playlist(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Playlist $id was not found"))
            call.respond(playlist)
        }
        get("/songs/{file}") {
            val file = call.parameters["file"].orEmpty()
            if (!catalog.hasAudioFixture(file)) {
                return@get call.respond(HttpStatusCode.NotFound, ApiError("Audio fixture was not found"))
            }
            call.response.header(HttpHeaders.CacheControl, CacheControl.MaxAge(3600).toString())
            call.respondWav(AudioFixture.wavFor(file))
        }
        get("/covers/{file}") {
            val file = call.parameters["file"].orEmpty()
            if (!file.endsWith(".svg")) {
                return@get call.respond(HttpStatusCode.NotFound, ApiError("Cover fixture was not found"))
            }
            val id = file.removeSuffix(".svg").toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Cover fixture was not found"))
            if (catalog.feed.flatMap(Section::albums).none { it.id == id }) {
                return@get call.respond(HttpStatusCode.NotFound, ApiError("Cover fixture was not found"))
            }
            call.response.header(HttpHeaders.CacheControl, CacheControl.MaxAge(3600).toString())
            call.respondText(CoverFixture.svgFor(id), ContentType.parse("image/svg+xml"))
        }
    }
}

private suspend fun ApplicationCall.respondWav(bytes: ByteArray) {
    response.header(HttpHeaders.AcceptRanges, "bytes")
    val rangeHeader = request.header(HttpHeaders.Range)
    if (rangeHeader == null) {
        respondBytes(bytes, ContentType.parse("audio/wav"))
        return
    }

    val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(rangeHeader)
    val requested = match?.let {
        val startText = it.groupValues[1]
        val endText = it.groupValues[2]
        when {
            startText.isNotEmpty() -> {
                val start = startText.toLongOrNull()
                val end = endText.toLongOrNull() ?: (bytes.size - 1).toLong()
                if (start == null || end < start) null else start..end
            }
            endText.isNotEmpty() -> {
                val suffixLength = endText.toLongOrNull()
                if (suffixLength == null || suffixLength <= 0) null
                else (bytes.size - suffixLength.coerceAtMost(bytes.size.toLong()))..(bytes.size - 1).toLong()
            }
            else -> null
        }
    }
    val start = requested?.first
    val end = requested?.last?.coerceAtMost((bytes.size - 1).toLong())
    if (start == null || end == null || start !in 0 until bytes.size.toLong()) {
        response.header(HttpHeaders.ContentRange, "bytes */${bytes.size}")
        respondBytes(ByteArray(0), ContentType.parse("audio/wav"), HttpStatusCode.RequestedRangeNotSatisfiable)
        return
    }

    response.header(HttpHeaders.ContentRange, "bytes $start-$end/${bytes.size}")
    respondBytes(
        bytes.copyOfRange(start.toInt(), end.toInt() + 1),
        ContentType.parse("audio/wav"),
        HttpStatusCode.PartialContent,
    )
}
