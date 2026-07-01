package my.utem.ftmk.masakgramprompt.model;

import java.time.LocalDateTime;

/**
 * Represents an experiment row with joined model, prompt, and result summary fields.
 */
public class ExperimentRecord {

    private int experimentId;
    private int transcriptId;
    private String modelName;
    private String modelTag;
    private String techniqueName;
    private boolean ragEnabled;
    private String status;
    private LocalDateTime executedAt;
    private Long processingTimeMs;
    private Integer nutritionResultId;
    private Boolean nutritionJsonValid;
    private String nutritionRecipeName;
    private Integer nutritionServingsEstimated;
    private Float nutritionTotalCalories;
    private Float nutritionServingCalories;
    private Float nutritionServingProteinG;
    private Float nutritionServingCarbohydrateG;
    private Float nutritionServingTotalFatG;
    private String nutritionRawJsonOutput;

    public int getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(int experimentId) {
        this.experimentId = experimentId;
    }

    public int getTranscriptId() {
        return transcriptId;
    }

    public void setTranscriptId(int transcriptId) {
        this.transcriptId = transcriptId;
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

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public void setRagEnabled(boolean ragEnabled) {
        this.ragEnabled = ragEnabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Integer getNutritionResultId() {
        return nutritionResultId;
    }

    public void setNutritionResultId(Integer nutritionResultId) {
        this.nutritionResultId = nutritionResultId;
    }

    public Boolean getNutritionJsonValid() {
        return nutritionJsonValid;
    }

    public void setNutritionJsonValid(Boolean nutritionJsonValid) {
        this.nutritionJsonValid = nutritionJsonValid;
    }

    public String getNutritionRecipeName() {
        return nutritionRecipeName;
    }

    public void setNutritionRecipeName(String nutritionRecipeName) {
        this.nutritionRecipeName = nutritionRecipeName;
    }

    public Integer getNutritionServingsEstimated() {
        return nutritionServingsEstimated;
    }

    public void setNutritionServingsEstimated(Integer nutritionServingsEstimated) {
        this.nutritionServingsEstimated = nutritionServingsEstimated;
    }

    public Float getNutritionTotalCalories() {
        return nutritionTotalCalories;
    }

    public void setNutritionTotalCalories(Float nutritionTotalCalories) {
        this.nutritionTotalCalories = nutritionTotalCalories;
    }

    public Float getNutritionServingCalories() {
        return nutritionServingCalories;
    }

    public void setNutritionServingCalories(Float nutritionServingCalories) {
        this.nutritionServingCalories = nutritionServingCalories;
    }

    public Float getNutritionServingProteinG() {
        return nutritionServingProteinG;
    }

    public void setNutritionServingProteinG(Float nutritionServingProteinG) {
        this.nutritionServingProteinG = nutritionServingProteinG;
    }

    public Float getNutritionServingCarbohydrateG() {
        return nutritionServingCarbohydrateG;
    }

    public void setNutritionServingCarbohydrateG(Float nutritionServingCarbohydrateG) {
        this.nutritionServingCarbohydrateG = nutritionServingCarbohydrateG;
    }

    public Float getNutritionServingTotalFatG() {
        return nutritionServingTotalFatG;
    }

    public void setNutritionServingTotalFatG(Float nutritionServingTotalFatG) {
        this.nutritionServingTotalFatG = nutritionServingTotalFatG;
    }

    public String getNutritionRawJsonOutput() {
        return nutritionRawJsonOutput;
    }

    public void setNutritionRawJsonOutput(String nutritionRawJsonOutput) {
        this.nutritionRawJsonOutput = nutritionRawJsonOutput;
    }
}
