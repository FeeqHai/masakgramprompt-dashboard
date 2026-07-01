package my.utem.ftmk.masakgramprompt.model;

import java.time.Duration;
import java.time.LocalDateTime;

public class SingleRunStatus {

    private boolean running;
    private boolean completed;
    private boolean failed;
    private Integer reelId;
    private String reelInstagramId;
    private Integer modelId;
    private String modelName;
    private Integer techniqueId;
    private String techniqueName;
    private String stage;
    private String resultUrl;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public Integer getReelId() {
        return reelId;
    }

    public void setReelId(Integer reelId) {
        this.reelId = reelId;
    }

    public String getReelInstagramId() {
        return reelInstagramId;
    }

    public void setReelInstagramId(String reelInstagramId) {
        this.reelInstagramId = reelInstagramId;
    }

    public Integer getModelId() {
        return modelId;
    }

    public void setModelId(Integer modelId) {
        this.modelId = modelId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Integer getTechniqueId() {
        return techniqueId;
    }

    public void setTechniqueId(Integer techniqueId) {
        this.techniqueId = techniqueId;
    }

    public String getTechniqueName() {
        return techniqueName;
    }

    public void setTechniqueName(String techniqueName) {
        this.techniqueName = techniqueName;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
