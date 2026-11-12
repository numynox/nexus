package io.github.numynox.nexus.abeona.domain.intake

import io.github.numynox.nexus.abeona.data.FareType

// A printed fare name → Abeona's fare type and whether it binds to the
// booked trains; app/abeona/src/lib/intake/fare.ts. First match wins.

data class FareGuess(val fareType: FareType, val binding: Boolean)

private val Rules: List<Pair<Regex, FareGuess>> = listOf(
    Regex("""deutschland-?ticket|\bd-ticket|jobticket deutschland""", RegexOption.IGNORE_CASE) to FareGuess(FareType.Deutschlandticket, false),
    Regex("""bahncard\s*100""", RegexOption.IGNORE_CASE) to FareGuess(FareType.BahnCard100, false),
    // Trenitalia's Economy and Super Economy are its saver fares, bound to the train.
    Regex("""sparpreis|super ?spar|sparschiene|saver|\beconomy\b""", RegexOption.IGNORE_CASE) to FareGuess(FareType.Saver, true),
    // Trenitalia's Base can be changed to another train, like a Flexpreis.
    Regex("""flexpreis|flex ?ticket|normalpreis(?! nahverkehr)|fl[eé]x|^base$""", RegexOption.IGNORE_CASE) to FareGuess(FareType.Flex, false),
    Regex(
        "quer-durchs-land|sch[öo]nes-wochenende|l[äa]nder-?ticket|bayern-?ticket|niedersachsen-?ticket|sachsen-?ticket|" +
            "th[üu]ringen-?ticket|baden-w[üu]rttemberg-?ticket|hessenticket|nrw-?ticket|rheinland-pfalz-?ticket|saarland-?ticket|" +
            "brandenburg-berlin-?ticket|mecklenburg-vorpommern-?ticket|schleswig-holstein-?ticket|nahverkehr|regio|verbund",
        setOf(RegexOption.IGNORE_CASE),
    ) to FareGuess(FareType.Regional, false),
)

/** "other", unbound, when Abeona does not know the name. */
fun fareOf(name: String?): FareGuess =
    name?.let { n -> Rules.firstOrNull { it.first.containsMatchIn(n) }?.second } ?: FareGuess(FareType.Other, false)
