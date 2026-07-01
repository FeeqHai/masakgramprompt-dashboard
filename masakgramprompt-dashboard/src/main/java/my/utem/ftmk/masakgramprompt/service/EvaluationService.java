package my.utem.ftmk.masakgramprompt.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvaluationService {

    private final ReviewDashboardService reviewDashboardService;

    public EvaluationService(ReviewDashboardService reviewDashboardService) {
        this.reviewDashboardService = reviewDashboardService;
    }

    /**
     * Calculates per-experiment metrics from completed experiment rows.
     */
    public List<ExperimentEvaluation> completedExperimentEvaluations() {
        List<ExperimentEvaluation> evaluations = new ArrayList<>();
        for (ReviewDashboardService.CompletedExperimentKey key : reviewDashboardService.findCompletedExperimentKeys()) {
            ReviewDashboardService.ExperimentDetail experiment = reviewDashboardService
                    .findExperimentById(key.experimentId())
                    .orElse(ReviewDashboardService.ExperimentDetail.empty(
                            key.transcriptId(),
                            key.modelId(),
                            key.techniqueId()
                    ));
            List<ReviewDashboardService.IngredientDetail> groundTruth =
                    reviewDashboardService.loadGroundTruthIngredients(key.transcriptId());
            List<ReviewDashboardService.IngredientDetail> ai =
                    reviewDashboardService.loadAiIngredients(key.experimentId());
            List<ReviewDashboardService.IngredientComparisonRow> ingredientRows =
                    reviewDashboardService.compareIngredients(groundTruth, ai);
            ReviewDashboardService.NutritionValues groundTruthTotals =
                    reviewDashboardService.sumNutrition(groundTruth);
            ReviewDashboardService.NutritionValues aiTotals = experiment.totalNutrition().isEmpty()
                    ? reviewDashboardService.sumNutrition(ai)
                    : experiment.totalNutrition();
            List<ReviewDashboardService.NutritionComparisonRow> nutritionRows =
                    reviewDashboardService.compareNutrition(groundTruthTotals, aiTotals);
            ReviewDashboardService.EvaluationMetrics metrics =
                    reviewDashboardService.calculateMetrics(ingredientRows, ai, experiment);

            evaluations.add(new ExperimentEvaluation(
                    key.experimentId(),
                    key.transcriptId(),
                    key.modelId(),
                    key.modelName(),
                    key.techniqueId(),
                    key.techniqueName(),
                    experiment,
                    ingredientRows,
                    nutritionRows,
                    metrics
            ));
        }
        return evaluations;
    }

    public List<EvaluationAggregate> aggregateByModelTechnique() {
        Map<String, AggregateAccumulator> accumulators = new LinkedHashMap<>();
        for (ExperimentEvaluation evaluation : completedExperimentEvaluations()) {
            String key = evaluation.modelId() + ":" + evaluation.techniqueId();
            AggregateAccumulator accumulator = accumulators.computeIfAbsent(
                    key,
                    value -> new AggregateAccumulator(evaluation.modelName(), evaluation.techniqueName())
            );
            accumulator.add(evaluation);
        }
        return accumulators.values().stream()
                .map(AggregateAccumulator::toAggregate)
                .toList();
    }

    private static final class AggregateAccumulator {

        private final String modelName;
        private final String techniqueName;
        private int completedExperiments;
        private double precisionTotal;
        private double recallTotal;
        private double f1Total;
        private double calorieErrorTotal;
        private double proteinErrorTotal;
        private double fatErrorTotal;
        private double carbErrorTotal;
        private int calorieErrorCount;
        private int proteinErrorCount;
        private int fatErrorCount;
        private int carbErrorCount;
        private int jsonValidCount;
        private int hallucinationCount;
        private int aiIngredientCount;

        private AggregateAccumulator(String modelName, String techniqueName) {
            this.modelName = modelName;
            this.techniqueName = techniqueName;
        }

        private void add(ExperimentEvaluation evaluation) {
            ReviewDashboardService.EvaluationMetrics metrics = evaluation.metrics();
            completedExperiments++;
            precisionTotal += metrics.precision();
            recallTotal += metrics.recall();
            f1Total += metrics.f1();
            if (metrics.jsonValid()) {
                jsonValidCount++;
            }
            hallucinationCount += metrics.hallucinationCount();
            aiIngredientCount += metrics.aiCount();
            addNutritionError(evaluation, "calories");
            addNutritionError(evaluation, "protein_g");
            addNutritionError(evaluation, "total_fat_g");
            addNutritionError(evaluation, "total_carbohydrate_g");
        }

        private void addNutritionError(ExperimentEvaluation evaluation, String nutritionKey) {
            Double error = evaluation.absoluteNutritionError(nutritionKey);
            if (error == null) {
                return;
            }
            switch (nutritionKey) {
                case "calories" -> {
                    calorieErrorTotal += error;
                    calorieErrorCount++;
                }
                case "protein_g" -> {
                    proteinErrorTotal += error;
                    proteinErrorCount++;
                }
                case "total_fat_g" -> {
                    fatErrorTotal += error;
                    fatErrorCount++;
                }
                case "total_carbohydrate_g" -> {
                    carbErrorTotal += error;
                    carbErrorCount++;
                }
                default -> {
                }
            }
        }

        private EvaluationAggregate toAggregate() {
            return new EvaluationAggregate(
                    modelName,
                    techniqueName,
                    completedExperiments,
                    average(precisionTotal, completedExperiments),
                    average(recallTotal, completedExperiments),
                    average(f1Total, completedExperiments),
                    average(calorieErrorTotal, calorieErrorCount),
                    average(proteinErrorTotal, proteinErrorCount),
                    average(fatErrorTotal, fatErrorCount),
                    average(carbErrorTotal, carbErrorCount),
                    average(jsonValidCount, completedExperiments),
                    hallucinationCount,
                    aiIngredientCount == 0 ? 0.0 : round((double) hallucinationCount / aiIngredientCount)
            );
        }

        private double average(double total, int count) {
            return count == 0 ? 0.0 : round(total / count);
        }

        private double round(double value) {
            return Math.round(value * 1000.0) / 1000.0;
        }
    }

    public record ExperimentEvaluation(
            int experimentId,
            int transcriptId,
            int modelId,
            String modelName,
            int techniqueId,
            String techniqueName,
            ReviewDashboardService.ExperimentDetail experiment,
            List<ReviewDashboardService.IngredientComparisonRow> ingredientRows,
            List<ReviewDashboardService.NutritionComparisonRow> nutritionRows,
            ReviewDashboardService.EvaluationMetrics metrics
    ) {
        public Double absoluteNutritionError(String nutritionKey) {
            return nutritionRows.stream()
                    .filter(row -> row.key().equals(nutritionKey))
                    .findFirst()
                    .map(ReviewDashboardService.NutritionComparisonRow::difference)
                    .map(Math::abs)
                    .orElse(null);
        }
    }

    public record EvaluationAggregate(
            String modelName,
            String techniqueName,
            int completedExperiments,
            double averagePrecision,
            double averageRecall,
            double averageF1,
            double averageCalorieAbsoluteError,
            double averageProteinAbsoluteError,
            double averageFatAbsoluteError,
            double averageCarbAbsoluteError,
            double jsonValidityRate,
            int hallucinationCount,
            double hallucinationRate
    ) {
    }
}
