package my.utem.ftmk.masakgramprompt.model;

/**
 * Represents the saved nutrition_result summary for one experiment.
 */
public class NutritionResultRecord {

    private int resultId;
    private int experimentId;
    private String modelName;
    private String modelTag;
    private String techniqueName;
    private String recipeName;
    private Integer servingsEstimated;
    private Float servingCalories;
    private Float servingProteinG;
    private Float servingCarbohydrateG;
    private Float servingTotalFatG;
    private Float totalCalories;
    private Float totalProteinG;
    private Float totalCarbohydrateG;
    private Float totalFatG;
    private Boolean jsonValid;
    private String rawJsonOutput;

    public int getResultId() {
        return resultId;
    }

    public void setResultId(int resultId) {
        this.resultId = resultId;
    }

    public int getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(int experimentId) {
        this.experimentId = experimentId;
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

    public String getTechniqueName() {
        return techniqueName;
    }

    public void setTechniqueName(String techniqueName) {
        this.techniqueName = techniqueName;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public void setRecipeName(String recipeName) {
        this.recipeName = recipeName;
    }

    public Integer getServingsEstimated() {
        return servingsEstimated;
    }

    public void setServingsEstimated(Integer servingsEstimated) {
        this.servingsEstimated = servingsEstimated;
    }

    public Float getServingCalories() {
        return servingCalories;
    }

    public void setServingCalories(Float servingCalories) {
        this.servingCalories = servingCalories;
    }

    public Float getServingProteinG() {
        return servingProteinG;
    }

    public void setServingProteinG(Float servingProteinG) {
        this.servingProteinG = servingProteinG;
    }

    public Float getServingCarbohydrateG() {
        return servingCarbohydrateG;
    }

    public void setServingCarbohydrateG(Float servingCarbohydrateG) {
        this.servingCarbohydrateG = servingCarbohydrateG;
    }

    public Float getServingTotalFatG() {
        return servingTotalFatG;
    }

    public void setServingTotalFatG(Float servingTotalFatG) {
        this.servingTotalFatG = servingTotalFatG;
    }

    public Float getTotalCalories() {
        return totalCalories;
    }

    public void setTotalCalories(Float totalCalories) {
        this.totalCalories = totalCalories;
    }

    public Float getTotalProteinG() {
        return totalProteinG;
    }

    public void setTotalProteinG(Float totalProteinG) {
        this.totalProteinG = totalProteinG;
    }

    public Float getTotalCarbohydrateG() {
        return totalCarbohydrateG;
    }

    public void setTotalCarbohydrateG(Float totalCarbohydrateG) {
        this.totalCarbohydrateG = totalCarbohydrateG;
    }

    public Float getTotalFatG() {
        return totalFatG;
    }

    public void setTotalFatG(Float totalFatG) {
        this.totalFatG = totalFatG;
    }

    public Boolean getJsonValid() {
        return jsonValid;
    }

    public void setJsonValid(Boolean jsonValid) {
        this.jsonValid = jsonValid;
    }

    public String getRawJsonOutput() {
        return rawJsonOutput;
    }

    public void setRawJsonOutput(String rawJsonOutput) {
        this.rawJsonOutput = rawJsonOutput;
    }
}
