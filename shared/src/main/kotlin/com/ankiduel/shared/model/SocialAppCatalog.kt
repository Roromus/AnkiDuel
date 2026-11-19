package com.ankiduel.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SocialAppInfo(
    val packageName: String,
    val displayName: String,
    val defaultSelected: Boolean = true
)

object SocialAppCatalog {
    const val ANKIDROID_PACKAGE = "com.ichi2.anki"

    val defaultSocialApps: List<SocialAppInfo> = listOf(
        SocialAppInfo("com.instagram.android", "Instagram"),
        SocialAppInfo("com.zhiliaoapp.musically", "TikTok"),
        SocialAppInfo("com.ss.android.ugc.trill", "TikTok (Alt)"),
        SocialAppInfo("com.twitter.android", "X (Twitter)"),
        SocialAppInfo("com.google.android.youtube", "YouTube"),
        SocialAppInfo("com.reddit.android", "Reddit"),
        SocialAppInfo("com.snapchat.android", "Snapchat"),
        SocialAppInfo("com.facebook.katana", "Facebook"),
        SocialAppInfo("com.instagram.barcelona", "Threads"),
        SocialAppInfo("tv.twitch.android.app", "Twitch"),
        SocialAppInfo("com.pinterest", "Pinterest", defaultSelected = false),
        SocialAppInfo("com.discord", "Discord", defaultSelected = false)
    )

    val defaultSelectedPackages: Set<String> = defaultSocialApps
        .filter { it.defaultSelected }
        .map { it.packageName }
        .toSet()

    fun displayNameFor(packageName: String): String =
        defaultSocialApps.firstOrNull { it.packageName == packageName }?.displayName
            ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercaseChar() }
}
