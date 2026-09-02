package com.factory.timetilescountdownwidgets.premium

/** Caps applied to free-tier users. Crossing any of these triggers the paywall. */
object FreeTierLimits {
    const val MAX_COUNTDOWNS = 3
    const val FREE_EMOJI_COUNT = 6
    const val FREE_COLOR_COUNT = 4
}

/** Feature highlights shown on the paywall. */
enum class PremiumFeature(val title: String, val description: String) {
    UNLIMITED_COUNTDOWNS(
        "Unlimited countdowns",
        "Free plan is capped at ${FreeTierLimits.MAX_COUNTDOWNS} — go premium for as many as you want."
    ),
    FULL_EMOJI_LIBRARY(
        "Full emoji library",
        "Unlock every emoji to personalize your countdowns."
    ),
    FULL_COLOR_PALETTE(
        "Full color palette",
        "Unlock every accent color."
    ),
    RECURRING_EVENTS(
        "Recurring countdowns",
        "Automatically repeat birthdays and anniversaries every year."
    ),
    AD_FREE(
        "Ad-free experience",
        "Remove banner ads across the app."
    ),
    PRIORITY_SUPPORT(
        "Priority support",
        "Get faster help when you need it."
    )
}
