package com.laioffer.spotify.images

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the application's actual singleton loader, not a test-only decoder. */
@RunWith(AndroidJUnit4::class)
class SvgImageLoaderTest {
    @Test
    fun applicationImageLoaderDecodesSvgCover() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#1ed760"/></svg>"""
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(svg.toByteArray()).size(32, 32).build(),
        )
        assertTrue("Expected actual SVG decode, got $result", result is SuccessResult)
        assertEquals(32, (result as SuccessResult).drawable.intrinsicWidth)
    }
}
