package my.utem.ftmk.masakgramprompt.model;

import java.time.Duration;
import java.time.LocalDateTime;

public class BatchRunStatus {

    private boolean running;
    private int totalRuns;
    private int completedRuns;
    private int failedRuns;
    private long totalProcessingTimeMs;
    private Long lastProcessingTimeMs;
    private Integer currentReelId;
    private String currentReelInstagramId;
    private String modelName;
    private String techniqueName;
<<<<<<< HEAD
    private String stage;
=======
    private String currentStage;
>>>>>>> branch 'master' of https://github.com/FeeqHai/masakgramprompt-dashboard.git
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public int getTotalRuns() {
        return totalRuns;
    }

    public void setTotalRuns(int totalRuns) {
        this.totalRuns = totalRuns;
    }

    public int getCompletedRuns() {
        return completedRuns;
    }

    public void setCompletedRuns(int completedRuns) {
        this.completedRuns = completedRuns;
    }

    public int getFailedRuns() {
        return failedRuns;
    }

    public void setFailedRuns(int failedRuns) {
        this.failedRuns = failedRuns;
    }

    public long getTotalProcessingTimeMs() {
        return totalProcessingTimeMs;
    }

    public void setTotalProcessingTimeMs(long totalProcessingTimeMs) {
        this.totalProcessingTimeMs = totalProcessingTimeMs;
    }

    public Long getLastProcessingTimeMs() {
        return lastProcessingTimeMs;
    }

    public void setLastProcessingTimeMs(Long lastProcessingTimeMs) {
        this.lastProcessingTimeMs = lastProcessingTimeMs;
    }

    public Long getAverageProcessingTimeMs() {
        int processedRuns = completedRuns + failedRuns;
        return processedRuns == 0 ? null : totalProcessingTimeMs / processedRuns;
    }

    public Integer getCurrentReelId() {
        return currentReelId;
    }

    public void setCurrentReelId(Integer currentReelId) {
        this.currentReelId = currentReelId;
    }

    public String getCurrentReelInstagramId() {
        return currentReelInstagramId;
    }

    public void setCurrentReelInstagramId(String currentReelInstagramId) {
        this.currentReelInstagramId = currentReelInstagramId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getTechniqueName() {
        return techniqueName;
    }

    public void setTechniqueName(String techniqueName) {
        this.techniqueName = techniqueName;
    }

<<<<<<< HEAD
    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
=======
    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
>>>>>>> branch 'master' of https://github.com/FeeqHai/masakgramprompt-dashboard.git
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public long getElapsedSeconds() {
        if (startedAt == null) {
            return 0;
        }
        LocalDateTime end = finishedAt == null ? LocalDateTime.now() : finishedAt;
        return Math.max(0, Duration.between(startedAt, end).toSeconds());
    }
}
