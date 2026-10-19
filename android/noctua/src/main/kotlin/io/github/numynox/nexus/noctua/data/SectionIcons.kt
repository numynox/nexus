package io.github.numynox.nexus.noctua.data

/** Section icon keys, in the web's order (app/noctua/src/lib/sectionMeta.ts). */
const val DEFAULT_SECTION_ICON = "newspaper"

data class SectionIconKey(val key: String, val label: String)

val SectionIconKeys = listOf(
    SectionIconKey("newspaper", "News"),
    SectionIconKey("globe", "World"),
    SectionIconKey("landmark", "Politics"),
    SectionIconKey("cpu", "Technology"),
    SectionIconKey("atom", "Science"),
    SectionIconKey("rocket", "Space"),
    SectionIconKey("briefcase", "Business"),
    SectionIconKey("scale", "Law"),
    SectionIconKey("trophy", "Sport"),
    SectionIconKey("dumbbell", "Fitness"),
    SectionIconKey("gamepad", "Games"),
    SectionIconKey("clapperboard", "Film"),
    SectionIconKey("music", "Music"),
    SectionIconKey("camera", "Photography"),
    SectionIconKey("utensils", "Food"),
    SectionIconKey("leaf", "Environment"),
    SectionIconKey("heart", "Health"),
    SectionIconKey("graduation-cap", "Education"),
    SectionIconKey("plane", "Travel"),
    SectionIconKey("lightbulb", "Ideas"),
)
