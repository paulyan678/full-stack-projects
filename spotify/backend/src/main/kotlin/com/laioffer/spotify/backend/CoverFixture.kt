package com.laioffer.spotify.backend

object CoverFixture {
    private val palettes = listOf(
        "#5B21B6" to "#EC4899",
        "#064E3B" to "#34D399",
        "#7C2D12" to "#FB923C",
        "#1E3A8A" to "#38BDF8",
        "#701A75" to "#E879F9",
    )

    fun svgFor(id: Int): String {
        val (start, end) = palettes[Math.floorMod(id - 1, palettes.size)]
        val number = id.toString().padStart(2, '0')
        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600">
              <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="$start"/><stop offset="1" stop-color="$end"/>
              </linearGradient></defs>
              <rect width="600" height="600" rx="28" fill="url(#g)"/>
              <circle cx="300" cy="300" r="178" fill="none" stroke="white" stroke-opacity=".18" stroke-width="34"/>
              <circle cx="300" cy="300" r="54" fill="white" fill-opacity=".88"/>
              <text x="42" y="82" fill="white" font-family="sans-serif" font-weight="700" font-size="34">LOCAL SESSIONS</text>
              <text x="42" y="548" fill="white" font-family="sans-serif" font-weight="800" font-size="92">$number</text>
            </svg>
        """.trimIndent()
    }
}
