package com.example.instadmguard.model

enum class InstagramScreen(
    val displayName: String,
    val isReelSurface: Boolean,
    val isBlockTarget: Boolean = isReelSurface,
) {
    OTHER("Other", false),
    DM_LIST("DM list", false),
    DM_THREAD("DM thread", false),
    REEL_VIEWER("Reel viewer", true),
    REELS_TAB("Reels tab", true),
    EXPLORE_REELS("Explore reels", true),
    HOME_REEL("Home reel", true, false),
}
