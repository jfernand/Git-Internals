package gitinternals

import java.time.Instant
import java.time.ZoneOffset

data class Tree(
    override val header: ObjectHeader,
    override val sha: String,
    val entries: List<FileEntry>,
) : GitObject {
    override fun toString(): String = "*TREE*\n${entries.joinToString("\n") { it.toString() }}"
}

data class Blob(
    override val header: ObjectHeader,
    override val sha: String,
    val content: String,
) : GitObject {
    override fun toString(): String = "*BLOB*\n$content"
}

data class FileEntry(val perms: String, val name: String, val sha: String) {
    override fun toString(): String = "$perms $sha $name"
}

sealed interface GitObject {
    enum class Type {
        BLOB, COMMIT, TREE
    }

    val header: ObjectHeader
    val sha: String
}

sealed interface Commit

data class InitialCommit(
    override val header: ObjectHeader,
    override val sha: String,
    val tree: String,
    val author: String,
    val originalTimestamp: Instant,
    val originalTimezone: String,
    val committer: String,
    val commitTimestamp: Instant,
    val commitTimezone: String,
    val message: String,
) : Commit, GitObject {
    override fun toString(): String = """
        |*COMMIT*
        |tree: $tree
        |author: $author original timestamp: ${dateTime(originalTimestamp, originalTimezone)}
        |committer: $committer commit timestamp: ${dateTime(commitTimestamp, commitTimezone)}
        |commit message:
        |$message
        """.trimMargin()

    fun toLogEntry(): String = """
    |Commit: $sha
    |$committer commit timestamp: ${dateTime(commitTimestamp, commitTimezone)}
    |$message
    """.trimMargin()
}

data class MergeCommit(
    override val header: ObjectHeader,
    override val sha: String,
    val tree: String,
    val parent: String,
    val mergedParent: String,
    val author: String,
    val originalTimestamp: Instant,
    val originalTimezone: String,
    val committer: String,
    val commitTimestamp: Instant,
    val commitTimezone: String,
    val message: String,
) : Commit, GitObject {
    override fun toString(): String = """
    |*COMMIT*
    |tree: $tree
    |parents: $parent | $mergedParent
    |author: $author original timestamp: ${dateTime(originalTimestamp, originalTimezone)}
    |committer: $committer commit timestamp: ${dateTime(commitTimestamp, commitTimezone)}
    |commit message:
    |$message
    """.trimMargin()

    fun toLogEntry(): String = """
    |Commit: $sha
    |$committer commit timestamp: ${dateTime(commitTimestamp, commitTimezone)}
    |$message
    |
    """.trimMargin()
}

data class SimpleCommit(
    override val header: ObjectHeader,
    override val sha: String,
    val tree: String,
    val parent: String,
    val author: String,
    val originalTimestamp: Instant,
    val originalTimezone: String,
    val committer: String,
    val commitTimestamp: Instant,
    val commitTimezone: String,
    val message: String,
) : Commit, GitObject {
    override fun toString(): String = """
    |*COMMIT*
    |tree: $tree
    |parents: $parent
    |author: $author original timestamp: ${dateTime(originalTimestamp, originalTimezone)}
    |committer: $committer commit timestamp: ${dateTime(commitTimestamp, commitTimezone)}
    |commit message:
    |$message
    """.trimMargin()

    fun toLogEntry(): String = """
    |Commit: $sha
    |$committer commit timestamp: ${dateTime(commitTimestamp, commitTimezone)}
    |$message
    """.trimMargin()
}

private fun dateTime(instant: Instant, timezone: String) =
    instant.atOffset(ZoneOffset.of(timezone))
        .toString()
        .replace('T', ' ')
        .replace(Regex("[+-][0-1][0-9]:00")) { " ${it.value}" }
