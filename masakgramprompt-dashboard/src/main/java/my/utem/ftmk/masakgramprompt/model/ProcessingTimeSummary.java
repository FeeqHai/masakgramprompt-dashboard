package my.utem.ftmk.masakgramprompt.model;

public class ProcessingTimeSummary {

    private String modelName;
    private String modelTag;
    private String techniqueName;
    private Integer completedRuns;
    private Double averageSeconds;
    private Double minSeconds;
    private Double maxSeconds;

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelTag() {
        return modelTag;
    }

    public void setModelTag(String modelTag) {
        this.modelTag = modelTag;
    }

    public String getTechniqueName() {
        return techniqueName;
    }

    public void setTechniqueName(String techniqueName) {
        this.techniqueName = techniqueName;
    }

    public Integer getCompletedRuns() {
        return completedRuns;
    }

    public void setCompletedRuns(Integer completedRuns) {
        this.completedRuns = completedRuns;
    }

    public Double getAverageSeconds() {
        return averageSeconds;
    }

    public void setAverageSeconds(Double averageSeconds) {
        this.averageSeconds = averageSeconds;
    }

    public Double getMinSeconds() {
        return minSeconds;
    }

    public void setMinSeconds(Double minSeconds) {
        this.minSeconds = minSeconds;
    }

    public Double getMaxSeconds() {
        return maxSeconds;
    }

    public void setMaxSeconds(Double maxSeconds) {
        this.maxSeconds = maxSeconds;
    }
}
