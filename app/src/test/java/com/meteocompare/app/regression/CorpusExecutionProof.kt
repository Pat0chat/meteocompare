package com.meteocompare.app.regression

import org.junit.Assert.assertEquals

/**
 * Verifiable evidence that every fixture row was actually passed to production Kotlin.
 * The exact count is intentionally independent from the generated TSV manifest.
 * Gradle captures the receipt in the JUnit XML's <system-out> section.
 */
object CorpusExecutionProof {
    private val knownFiles = setOf(
        "wmo.tsv", "clouds.tsv", "daily.tsv", "consensus.tsv", "sunshine.tsv",
        "sunshine_consensus.tsv", "widget_layout.tsv", "widget_metrics.tsv", "widget_refresh.tsv"
    )

    fun completed(file: String, count: Int, expected: Int, startedAt: Long) {
        require(file in knownFiles) { "Unregistered corpus fixture $file" }
        assertEquals("Incomplete execution of corpus $file", expected, count)
        val elapsedMs = ((System.nanoTime() - startedAt).coerceAtLeast(0L)) / 1_000_000L
        // Keep this stable: scripts/report_test_results.py parses it from real JUnit XML.
        println("METEOCOMPARE_CORPUS_V1 file=$file cases=$count elapsed_ms=$elapsedMs")
    }
}
