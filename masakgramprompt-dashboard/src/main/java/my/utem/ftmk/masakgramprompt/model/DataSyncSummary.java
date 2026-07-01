package my.utem.ftmk.masakgramprompt.model;

/**
 * Summarizes how many local dataset files were found or missing during sync.
 */
public record DataSyncSummary(int audioFilesSynced, int transcriptsSynced, int missingAudioFiles, int missingTranscriptFiles) {
}
