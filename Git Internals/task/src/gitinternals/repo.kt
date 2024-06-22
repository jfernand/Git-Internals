package gitinternals

import java.io.File
import java.util.zip.InflaterInputStream

class Repository(location: String) {
    private val location: File = File(location).also { it.exists() }

    fun getBranches() =
        File(location, "refs/heads")
            .list()
            ?.asList() ?: listOf()

    fun getCommit(sha: String): GitObject {
        val (index, remainder) = (splitSha(sha))
        val file = File(location, "objects/$index/$remainder")
        val input = InflaterInputStream(file.inputStream())
        val header = input.readObjectHeader()
        val gitObject = input.toGitObject(header, sha)
        return gitObject
    }

    fun getBranch(branch: String) =
        with(location) {
            File(this, "refs/heads/$branch")
                .readText()
                .replace("\n", "")
        }

    fun getCurrentBranch() =
        File(location, "HEAD")
            .readText()
            .replace("ref: refs/heads/", "")
            .replace("\n", "")

    fun walkTree(treeHash: String): List<String> {
        val tree = getCommit(treeHash)
        return when(tree) {
            is SimpleCommit -> error("$tree: Expecting tree commit")
            is Blob -> error("$tree: Expecting tree commit")
            is InitialCommit -> error("$tree: Expecting tree commit")
            is MergeCommit -> error("$tree: Expecting tree commit")
            is Tree -> tree.entries.flatMap { entry ->
                when(entry.perms) {
                    "40000" -> walkTree(entry.sha).map { "${entry.name}/${it}"}
                    else -> listOf(entry.name)
                }
            }
        }
    }
}

private fun String.getBranch(branch: String) =
    File(this).let {
        File("${this}/refs/heads/$branch")
            .readText()
            .replace("\n", "")
    }

private fun String.getCommit(sha: String): GitObject {
    val (index, remainder) = (splitSha(sha))
    val file = File("${this}/objects/$index/$remainder")
    val input = InflaterInputStream(file.inputStream())
    val header = input.readObjectHeader()
    val gitObject = input.toGitObject(header, sha)
    return gitObject
}

private fun String.getCurrentBranch(): String =
    File("${this}/HEAD")
        .readText()
        .replace("ref: refs/heads/", "")
        .replace("\n", "")

private fun String.getBranches(): List<String> =
    File("${this}/refs/heads")
        .list()
        ?.asList() ?: listOf()
