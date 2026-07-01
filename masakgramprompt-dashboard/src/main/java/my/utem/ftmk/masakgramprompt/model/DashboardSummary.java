package my.utem.ftmk.masakgramprompt.model;

/**
 * Holds the top-level dataset and experiment counters for the main dashboard.
 */
public class DashboardSummary {

    private int reelCount;
    private int audioCount;
    private int transcriptCount;
    private int groundTruthCount;
    private int experimentCount;
    private int pendingExperimentCount;
    private int runningExperimentCount;
    private int completedExperimentCount;
    private int failedExperimentCount;

    public int getReelCount() {
        return reelCount;
    }

    public void setReelCount(int reelCount) {
        this.reelCount = reelCount;
    }

    public int getAudioCount() {
        return audioCount;
    }

    public void setAudioCount(int audioCount) {
        this.audioCount = audioCount;
    }

    public int getTranscriptCount() {
        return transcriptCount;
    }

    public void setTranscriptCount(int transcriptCount) {
        this.transcriptCount = transcriptCount;
    }

    public int getGroundTruthCount() {
        return groundTruthCount;
    }

    public void setGroundTruthCount(int groundTruthCount) {
        this.groundTruthCount = groundTruthCount;
    }

    public int getExperimentCount() {
        return experimentCount;
    }

    public void setExperimentCount(int experimentCount) {
        this.experimentCount = experimentCount;
    }

    public int getPendingExperimentCount() {
        return pendingExperimentCount;
    }

    public void setPendingExperimentCount(int pendingExperimentCount) {
        this.pendingExperimentCount = pendingExperimentCount;
    }

    public int getRunningExperimentCount() {
        return runningExperimentCount;
    }

    public void setRunningExperimentCount(int runningExperimentCount) {
        this.runningExperimentCount = runningExperimentCount;
    }

    public int getCompletedExperimentCount() {
        return completedExperimentCount;
    }

    public void setCompletedExperimentCount(int completedExperimentCount) {
        this.completedExperimentCount = completedExperimentCount;
    }

    public int getFailedExperimentCount() {
        return failedExperimentCount;
    }

    public void setFailedExperimentCount(int failedExperimentCount) {
        this.failedExperimentCount = failedExperimentCount;
    }
}
