package gitinternals

import gitinternals.GitObject.Type
import gitinternals.GitObject.Type.*
import java.io.InputStream
import java.time.Instant

data class ObjectHeader(val type: Type, val length: Int)

fun String.toGitObjectType(): Type =
    when (this) {
        "blob" -> BLOB
        "commit" -> COMMIT
        "tree" -> TREE
        else -> error("Unrecognized git type: $this")
    }

fun InputStream.readObjectHeader(): ObjectHeader {
    val headerString = String(readToNull().getOrThrow())
    val (type, length) = headerString.split(" ")
    return ObjectHeader(type.toGitObjectType(), length.toInt())
}

fun InputStream.toGitObject(header: ObjectHeader, sha:String): GitObject =
    when (header.type) {
        BLOB -> readBytes() // TODO migrate to read from stream
            .decodeToString()
            .replace("${0.toChar()}", "\n")
            .split("\n").toBlob(header, sha)

        COMMIT -> readBytes()
            .decodeToString()
            .replace("${0.toChar()}", "\n")
            .split("\n").toCommit(header, sha)

        TREE -> toTree(header, sha)
    }

private fun InputStream.toTree(header: ObjectHeader, sha:String): GitObject {
    return readFileEntries()
        .getOrThrow()
        .toTree(header, sha)
}

private fun List<FileEntry>.toTree(header: ObjectHeader, sha:String): GitObject = Tree(header,sha, this)

fun List<String>.parseTreeEntry(): ParseState {
    val (label, sha) = get(0).split(" ")
    if (label != "tree") error("Wrong label $label, expecting 'author'!")
    return ParseState(sha, tail = tail())
}

fun ParseState.parseFirstParent(): ParseState {
    val (label, parent) = tail.get(0).split(" ")
    return when (label) {
        "parent" -> copy(parent = parent, tail = tail.tail())
        else -> copy(tail = tail)
    }
}

fun ParseState.parents(): ParseState {
    val (label, mergedParent) = tail[0].split(" ")
    return when (label) {
        "parent" -> copy(
            parent = parent ?: error("parsing second parent without a first parent"),
            mergedParent = mergedParent,
            tail = tail.tail()
        )

        else -> copy(tail = tail)
    }
}

fun ParseState.author(): ParseState {
    val (label, author, email, timestamp, timezone) = tail[0].split(" ")
    if (label != "author") error("Wrong label $label, expecting 'author'!")
    return copy(
        author = "$author ${email.trimEmail()}",
        originalTimestamp = Instant.ofEpochSecond(timestamp.toLong()),
        originalTimezone = timezone,
        tail = tail.tail(),
    )
}

fun ParseState.committer(): ParseState {
    val (label, committer, email, timestamp, timezone) = tail[0].split(" ")
    if (label != "committer") error("Wrong label $label, expecting 'committer'!")
    return copy(
        committer = "$committer ${email.trimEmail()}",
        commitTimestamp = Instant.ofEpochSecond(timestamp.toLong()),
        commitTimezone = timezone,
        tail = tail.tail()
    )
}

private fun String.trimEmail(): String = replace("<", "").replace(">", "")

data class ParseState(
    val tree: String? = null,
    val parent: String? = null,
    val mergedParent: String? = null,
    val author: String? = null,
    val originalTimestamp: Instant? = null,
    val originalTimezone: String? = null,
    val committer: String? = null,
    val commitTimestamp: Instant? = null,
    val commitTimezone: String? = null,
    val commitMessage: String? = null,
    val tail: List<String>,
) {
    fun toCommit(header: ObjectHeader, sha: String): GitObject {
        if (parent == null) {
            return InitialCommit(
                header = header,
                sha = sha,
                tree = tree ?: error("Null tree"),
                author = author ?: error("Null author"),
                originalTimestamp = originalTimestamp ?: error("Null originalTimestamp"),
                originalTimezone = originalTimezone ?: error("Null originalTimezone"),
                committer = committer ?: error("Null committer"),
                commitTimestamp = commitTimestamp ?: error("Null commitTimestamp"),
                commitTimezone = commitTimezone ?: error("Null commitTimezone"),
                message = commitMessage ?: error("Null message"),
            )
        }
        if (mergedParent == null) {
            return SimpleCommit(
                header = header,
                sha = sha,
                tree = tree ?: error("Null tree"),
                parent = parent,
                author = author ?: error("Null author"),
                originalTimestamp = originalTimestamp ?: error("Null originalTimestamp"),
                originalTimezone = originalTimezone ?: error("Null originalTimezone"),
                committer = committer ?: error("Null committer"),
                commitTimestamp = commitTimestamp ?: error("Null commitTimestamp"),
                commitTimezone = commitTimezone ?: error("Null commitTimezone"),
                message = commitMessage ?: error("Null message"),
            )
        }
        return MergeCommit(
            header = header,
            sha = sha,
            tree = tree ?: error("Null tree"),
            parent = parent,
            mergedParent = mergedParent,
            author = author ?: error("Null author"),
            originalTimestamp = originalTimestamp ?: error("Null originalTimestamp"),
            originalTimezone = originalTimezone ?: error("Null originalTimezone"),
            committer = committer ?: error("Null committer"),
            commitTimestamp = commitTimestamp ?: error("Null commitTimestamp"),
            commitTimezone = commitTimezone ?: error("Null commitTimezone"),
            message = commitMessage ?: error("Null message"),
        )
    }
}

fun ParseState.message() = copy(
    commitMessage = tail.joinToString("\n").replaceFirst("\n", "")
)

fun <E> List<E>.tail() = this.drop(1)

fun List<String>.toBlob(header: ObjectHeader, sha: String): Blob = Blob(header, sha,
    joinToString("\n")
)

fun List<String>.toCommit(header: ObjectHeader, sha:String): GitObject =
    parseTreeEntry()
        .parseFirstParent()
        .parents()
        .author()
        .committer()
        .message()
        .toCommit(header, sha)

fun splitSha(sha: String) = sha.take(2) to sha.substring(2)
