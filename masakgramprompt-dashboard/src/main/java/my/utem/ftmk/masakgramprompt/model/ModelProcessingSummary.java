package my.utem.ftmk.masakgramprompt.model;

import java.util.List;

/**
 * Groups processing-time summaries for one model across prompt techniques.
 */
public class ModelProcessingSummary {

    private String modelName;
    private String modelTag;
    private String fastestTechniqueName;
    private List<ProcessingTimeSummary> techniqueSummaries;

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

    public String getFastestTechniqueName() {
        return fastestTechniqueName;
    }

    public void setFastestTechniqueName(String fastestTechniqueName) {
        this.fastestTechniqueName = fastestTechniqueName;
    }

    public List<ProcessingTimeSummary> getTechniqueSummaries() {
        return techniqueSummaries;
    }

    public void setTechniqueSummaries(List<ProcessingTimeSummary> techniqueSummaries) {
        this.techniqueSummaries = techniqueSummaries;
    }
}
