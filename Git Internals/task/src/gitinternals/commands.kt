package gitinternals

fun String.toCommand() = when (this) {
    "list-branches" -> ListBranches
    "cat-file" -> CatFile
    "log" -> Log
    "commit-tree" -> CommitTree
    else -> error("Wrong command")
}

sealed interface Command {
    fun execute(repo: Repository)
}

object Log : Command {
    override fun execute(repo: Repository) {
        val branch = prompt("Enter branch name:")
        repo.log(branch)
    }
}

fun prompt(message: String): String {
    println(message)
    val branch = readlnOrNull() ?: error("Whaaaaa")
    return branch
}

object CommitTree : Command {
    override fun execute(repo: Repository) {
        val hash = prompt("Enter commit-hash:")
        val treeHash = when(val commit = repo.getCommit(hash)) {
            is SimpleCommit -> commit.tree
            is Blob -> error("$hash is a blob commit")
            is InitialCommit -> commit.tree
            is MergeCommit -> commit.tree
            is Tree -> error("$hash is a tree commit")
        }
        val tree = repo.walkTree(treeHash)
        tree.forEach { println(it) }
    }
}

object ListBranches : Command {
    override fun execute(repo: Repository) {
        val branches = repo.getBranches()
        val currentBranch = repo.getCurrentBranch()
        errln(currentBranch)
        println(
            branches.sorted()
                .joinToString("\n") {
                    errln("$it $currentBranch|")
                    if (it == currentBranch) {
                        "* $it"
                    } else {
                        "  $it"
                    }
                }
        )
    }
}

object CatFile : Command {
    override fun execute(repo: Repository) {
        val hash = prompt("Enter git object hash:")

        repo.doExecute(hash)
    }

    fun Repository.doExecute(hash: String) {
        val gitObject = getCommit(hash)
        println(gitObject)
    }
}

private fun errln(s: Any) {
    System.err.println(s)
}


fun Repository.log(branch: String) {
    var sha: String? = getBranch(branch)
    while (sha != null) {
        val commit = getCommit(sha)
        sha = when (commit) {
            is Blob -> error("Unexpected blob during log")
            is InitialCommit -> {
                println(commit.toLogEntry())
                null
            }

            is MergeCommit -> {
                val mergeCommit = getCommit(commit.mergedParent)
                errln(mergeCommit)
                print(commit.toLogEntry())
                val merged = mergeCommit as? SimpleCommit
                println(
                    merged
                        ?.copy(sha = "${merged.sha} (merged)")
                        ?.toLogEntry()
                )
                commit.parent
            }

            is SimpleCommit -> {
                println(commit.toLogEntry())
                commit.parent
            }

            is Tree -> error("Unexpected tree during log")
        }
    }
}
