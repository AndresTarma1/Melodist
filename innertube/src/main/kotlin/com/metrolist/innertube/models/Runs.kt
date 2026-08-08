package com.metrolist.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
    val runs: List<Run>?,
)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint?,
)

fun List<Run>.splitBySeparator(): List<List<Run>> {
    val res = mutableListOf<List<Run>>()
    var tmp = mutableListOf<Run>()
    forEach { run ->
        if (run.text.trim() == "•") {
            res.add(tmp)
            tmp = mutableListOf()
        } else {
            tmp.add(run)
        }
    }
    res.add(tmp)
    return res
}

fun List<Run>.splitArtistsByConjunction(): List<Run> {
    return splitArtistRuns().filterNot { run ->
        isArtistSeparator(run.text)
    }
}

object ArtistConjunctions {
    var conjunctions: List<String> = listOf("and", "y")
}

fun List<List<Run>>.clean(): List<List<Run>> {
    val firstGroup = getOrNull(0) ?: return this
    val hasArtistSignals = firstGroup.any { it.navigationEndpoint != null } ||
        firstGroup.any { it.text.contains(" & ") } ||
        ArtistConjunctions.conjunctions.any { conj ->
            firstGroup.any { it.text.trim().equals(conj, ignoreCase = true) }
        }
    return if (hasArtistSignals) this else drop(1)
}

/**
 * Extracts the "entity" runs (artist/album names) from a mixed run list that also contains
 * separators, conjunctions ("y", "&", ...), and trailing metadata (view counts). Used across most
 * innertube artist/album parsing (18+ call sites).
 *
 * Prefers filtering by having a real browseId link rather than by index parity — a prior
 * `index % 2 == 0` implementation assumed artists and separators strictly alternate one-for-one,
 * which breaks as soon as a byline has an irregular separator count (e.g. more than 2 artists, or
 * mixed ", "/" y " separators): the wrong run lands on an "even" index, producing blank/duplicated
 * artist names and even sneaking the trailing view-count run into the result.
 *
 * Falls back to the old positional heuristic only when *no* run in the list has a link at all —
 * label-uploaded albums/tracks (non-YTM-channel uploaders) name the artist as plain, unlinked text,
 * so a strict link filter would wrongly return nothing for those instead of a best-effort guess.
 */
fun List<Run>.oddElements(): List<Run> {
    val linked = filter { run -> run.navigationEndpoint?.browseEndpoint?.browseId != null }
    if (linked.isNotEmpty()) return linked.splitArtistRuns()
    return filterIndexed { index, _ -> index % 2 == 0 }
}

/**
 * Separates artist names that YouTube sometimes returns in a single run, for example
 * `Artist A, Artist B y Artist C`. The endpoint belongs to the first name; the remaining
 * names are still useful for display even when YouTube does not provide individual IDs.
 */
fun List<Run>.splitArtistRuns(): List<Run> = flatMap { run ->
    val parts = run.text
        .split(Regex("\\s*(?:,|\\by\\b|\\band\\b|&)\\s*", RegexOption.IGNORE_CASE))
        .map(String::trim)
        .filter(String::isNotBlank)

    if (parts.isEmpty() || isArtistSeparator(run.text)) {
        emptyList()
    } else if (parts.size <= 1) {
        listOf(run)
    } else {
        parts.mapIndexed { index, name ->
            Run(name, if (index == 0) run.navigationEndpoint else null)
        }
    }
}

private fun isArtistSeparator(text: String): Boolean =
    text.trim().let { value ->
        value == "," || value == "•" || value == "&" ||
            ArtistConjunctions.conjunctions.any { value.equals(it, ignoreCase = true) }
    }
