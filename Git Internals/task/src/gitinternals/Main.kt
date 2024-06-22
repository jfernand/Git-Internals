package gitinternals

import java.io.File

fun main() {
    val location = prompt("Enter .git directory location:")
    val command = prompt("Enter command:")
    command
        .toCommand()
        .execute(Repository(File(location).absolutePath))
}
