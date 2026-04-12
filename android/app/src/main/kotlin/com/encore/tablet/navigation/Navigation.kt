package com.encore.tablet.navigation

/**
 * Navigation routes for the app.
 * The live NavHost is defined inline in MainScreen.kt.
 */
object Routes {
    const val LIBRARY = "library"
    const val SETLISTS = "setlists"
    const val SETLIST_DETAIL = "setlist/{setlistId}"
    // setNumber is optional (-1 = no set context; omit from URL when absent)
    const val SONG_DETAIL = "song/{songId}?setNumber={setNumber}"
    const val SONG_CHART_EDITOR = "chart_editor/{songId}"
    const val SETTINGS = "settings"

    fun setlistDetail(setlistId: String) = "setlist/$setlistId"
    fun chartEditor(songId: String) = "chart_editor/$songId"
    fun songDetail(songId: String, setNumber: Int? = null): String =
        if (setNumber != null && setNumber > 0) "song/$songId?setNumber=$setNumber"
        else "song/$songId"
}
