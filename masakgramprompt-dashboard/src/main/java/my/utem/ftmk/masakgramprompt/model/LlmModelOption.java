package my.utem.ftmk.masakgramprompt.model;

/**
 * Simple dropdown option for selecting an LLM model.
 */
public class LlmModelOption {

    private int modelId;
    private String modelName;
    private String modelTag;

    public int getModelId() {
        return modelId;
    }

    public void setModelId(int modelId) {
        this.modelId = modelId;
    }

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
}
