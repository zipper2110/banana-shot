package org.litvin.i18n

import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Lists the missing and the stale translations of each language (B-11 E4-T3). See [TranslationCheck].
 *
 * Run it from the repository root:
 * ```
 * mvn -q test-compile exec:java -Dexec.classpathScope=test -Dapp.mainClass=org.litvin.i18n.TranslationCheckMainKt
 * ```
 * Options (in `"-Dexec.args=..."`):
 * - `--fail-on-missing`: the exit code is 1 when a language has a missing key. The release workflow uses it.
 * - `--summary=<file>`: also add the report to this file. CI gives `$GITHUB_STEP_SUMMARY`.
 * - `--stamp=<code,code|all> --keys=<key,key>`: write the record of the current English text above these keys.
 *   Use it after a new translation of the keys. `--all-keys` in place of `--keys` writes the records of all keys
 *   of the languages. Use it only after a full new draft, because then no key shows as stale.
 * - `--resources=<folder>`: the folder of the `i18n` files. The default is `src/main/resources`.
 */
fun main(args: Array<String>) {
    val code = TranslationCheckMain.run(args.toList(), System.out)
    if (code != 0) exitProcess(code)
}

object TranslationCheckMain {
    fun run(args: List<String>, out: PrintStream): Int {
        val options = args.associate { arg -> arg.substringBefore('=') to arg.substringAfter('=', "") }
        val unknown = options.keys - OPTIONS
        if (unknown.isNotEmpty()) {
            out.println("Unknown options: ${unknown.joinToString()}. The options are: ${OPTIONS.joinToString()}.")
            return 2
        }
        val resources = File(options["--resources"]?.ifEmpty { null } ?: "src/main/resources")
        options["--stamp"]?.let { return stamp(resources, it, options, out) }

        val reports = TranslationCheck.checkAll(resources)
        val markdown = TranslationCheck.markdown(reports)
        out.print(markdown)
        options["--summary"]?.ifEmpty { null }?.let { File(it).appendText(markdown + "\n", Charsets.UTF_8) }
        val missing = reports.filter { it.missing.isNotEmpty() }
        if ("--fail-on-missing" in options && missing.isNotEmpty()) {
            out.println("The release stops: ${missing.joinToString { it.language.code }} have missing keys.")
            return 1
        }
        return 0
    }

    private fun stamp(resources: File, codes: String, options: Map<String, String>, out: PrintStream): Int {
        val languages = if (codes == "all") {
            AppLanguage.entries.filter { it != AppLanguage.SOURCE }
        } else {
            codes.split(',').map { code ->
                AppLanguage.ofCode(code)?.takeIf { it != AppLanguage.SOURCE } ?: run {
                    out.println("Unknown language: $code")
                    return 2
                }
            }
        }
        val keys = options["--keys"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        if (keys == null && "--all-keys" !in options || keys != null && "--all-keys" in options) {
            out.println("Give --keys=<key,key> or --all-keys.")
            return 2
        }
        val english = TranslationCheck.texts(TranslationCheck.files(resources, AppLanguage.SOURCE).map { it.readText(Charsets.UTF_8) })
        languages.forEach { language ->
            TranslationCheck.files(resources, language).forEach { file ->
                val content = file.readText(Charsets.UTF_8)
                val stamped = TranslationCheck.stamp(content, english) { keys == null || it in keys }
                if (stamped != content) {
                    file.writeText(stamped, Charsets.UTF_8)
                    out.println("Updated the records in $file")
                }
            }
        }
        return 0
    }

    private val OPTIONS = setOf("--fail-on-missing", "--summary", "--stamp", "--keys", "--all-keys", "--resources")
}
