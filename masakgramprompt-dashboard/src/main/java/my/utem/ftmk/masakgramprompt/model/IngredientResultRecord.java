package my.utem.ftmk.masakgramprompt.model;

/**
 * Represents one ingredient extracted from an LLM nutrition result.
 */
public class IngredientResultRecord {

    private Integer ingredientResultId;
    private Integer resultId;
    private String nameOriginal;
    private String nameEn;
    private Float quantityValue;
    private String unitOriginal;
    private String unitEn;
    private Float estimatedWeightG;
    private Float calories;
    private Float totalFatG;
    private Float saturatedFatG;
    private Float cholesterolMg;
    private Float sodiumMg;
    private Float totalCarbohydrateG;
    private Float dietaryFiberG;
    private Float totalSugarsG;
    private Float proteinG;
    private Float vitaminDMcg;
    private Float calciumMg;
    private Float ironMg;
    private Float potassiumMg;

    public Integer getIngredientResultId() {
        return ingredientResultId;
    }

    public void setIngredientResultId(Integer ingredientResultId) {
        this.ingredientResultId = ingredientResultId;
    }

    public Integer getResultId() {
        return resultId;
    }

    public void setResultId(Integer resultId) {
        this.resultId = resultId;
    }

    public String getNameOriginal() {
        return nameOriginal;
    }

    public void setNameOriginal(String nameOriginal) {
        this.nameOriginal = nameOriginal;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public Float getQuantityValue() {
        return quantityValue;
    }

    public void setQuantityValue(Float quantityValue) {
        this.quantityValue = quantityValue;
    }

    public String getUnitOriginal() {
        return unitOriginal;
    }

    public void setUnitOriginal(String unitOriginal) {
        this.unitOriginal = unitOriginal;
    }

    public String getUnitEn() {
        return unitEn;
    }

    public void setUnitEn(String unitEn) {
        this.unitEn = unitEn;
    }

    public Float getEstimatedWeightG() {
        return estimatedWeightG;
    }

    public void setEstimatedWeightG(Float estimatedWeightG) {
        this.estimatedWeightG = estimatedWeightG;
    }

    public Float getCalories() {
        return calories;
    }

    public void setCalories(Float calories) {
        this.calories = calories;
    }

    public Float getTotalFatG() {
        return totalFatG;
    }

    public void setTotalFatG(Float totalFatG) {
        this.totalFatG = totalFatG;
    }

    public Float getSaturatedFatG() {
        return saturatedFatG;
    }

    public void setSaturatedFatG(Float saturatedFatG) {
        this.saturatedFatG = saturatedFatG;
    }

    public Float getCholesterolMg() {
        return cholesterolMg;
    }

    public void setCholesterolMg(Float cholesterolMg) {
        this.cholesterolMg = cholesterolMg;
    }

    public Float getSodiumMg() {
        return sodiumMg;
    }

    public void setSodiumMg(Float sodiumMg) {
        this.sodiumMg = sodiumMg;
    }

    public Float getTotalCarbohydrateG() {
        return totalCarbohydrateG;
    }

    public void setTotalCarbohydrateG(Float totalCarbohydrateG) {
        this.totalCarbohydrateG = totalCarbohydrateG;
    }

    public Float getDietaryFiberG() {
        return dietaryFiberG;
    }

    public void setDietaryFiberG(Float dietaryFiberG) {
        this.dietaryFiberG = dietaryFiberG;
    }

    public Float getTotalSugarsG() {
        return totalSugarsG;
    }

    public void setTotalSugarsG(Float totalSugarsG) {
        this.totalSugarsG = totalSugarsG;
    }

    public Float getProteinG() {
        return proteinG;
    }

    public void setProteinG(Float proteinG) {
        this.proteinG = proteinG;
    }

    public Float getVitaminDMcg() {
        return vitaminDMcg;
    }

    public void setVitaminDMcg(Float vitaminDMcg) {
        this.vitaminDMcg = vitaminDMcg;
    }

    public Float getCalciumMg() {
        return calciumMg;
    }

    public void setCalciumMg(Float calciumMg) {
        this.calciumMg = calciumMg;
    }

    public Float getIronMg() {
        return ironMg;
    }

    public void setIronMg(Float ironMg) {
        this.ironMg = ironMg;
    }

    public Float getPotassiumMg() {
        return potassiumMg;
    }

    public void setPotassiumMg(Float potassiumMg) {
        this.potassiumMg = potassiumMg;
    }
}
