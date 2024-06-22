package gitinternals

import java.io.EOFException
import java.io.InputStream
import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

fun InputStream.readToNull(): Result<ByteArray> {
    var bytes = arrayOf<Byte>()
    var c = read()
    while (c != 0) {
        if (c == -1) return failure(EOFException("End of file reached"))
        bytes += c.toByte()
        c = read()
    }
    return success(bytes.toByteArray())
}

fun InputStream.readFileEntry(): Result<FileEntry> =
    readToNull()
        .flatMap { entry ->
            readSha()
                .map { sha ->
                    val splitEntry = String(entry).split(" ")
                    FileEntry(splitEntry[0], splitEntry.tail().joinToString("/") , sha.joinToString("") { byteToHex(it) })
                }
        }

private fun String.zeroPad() = if (length % 2 == 1) "0${this}" else this

private fun byteToHex(it: Byte) = it.toUByte().toString(16).zeroPad()

fun InputStream.readFileEntries(): Result<List<FileEntry>> {
    val entries = mutableListOf<FileEntry>()
    var result = readFileEntry()
    while (result.isSuccess) {
        System.err.println(result)
        System.err.println(result.getOrNull())
        entries.add(result.getOrThrow())
        result = readFileEntry()
    }
    return success(entries)
}

private fun InputStream.readSha(): Result<ByteArray> {
    val shaBytes = ByteArray(20)
    val bytesRead = read(shaBytes)
    if (bytesRead != 20) failure<EOFException>(EOFException("End of file reached"))
    return success(shaBytes)
}


fun <T, R> Result<T>.flatMap(block: (T) -> (Result<R>)): Result<R> {
    return this.mapCatching {
        block(it).getOrThrow()
    }
}
