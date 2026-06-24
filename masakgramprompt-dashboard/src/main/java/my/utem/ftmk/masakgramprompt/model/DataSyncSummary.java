package my.utem.ftmk.masakgramprompt.model;

public record DataSyncSummary(int audioFilesSynced, int transcriptsSynced, int missingAudioFiles, int missingTranscriptFiles) {
}
