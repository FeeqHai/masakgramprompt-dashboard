package my.utem.ftmk.masakgramprompt.controller;

import my.utem.ftmk.masakgramprompt.service.DashboardService;
import my.utem.ftmk.masakgramprompt.service.BatchExperimentService;
import my.utem.ftmk.masakgramprompt.service.DatasetImportService;
import my.utem.ftmk.masakgramprompt.service.ExcelExportService;
import my.utem.ftmk.masakgramprompt.service.LlmExperimentRunnerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final LlmExperimentRunnerService llmExperimentRunnerService;
    private final DatasetImportService datasetImportService;
    private final BatchExperimentService batchExperimentService;
    private final ExcelExportService excelExportService;

    public DashboardController(
            DashboardService dashboardService,
            LlmExperimentRunnerService llmExperimentRunnerService,
            DatasetImportService datasetImportService,
            BatchExperimentService batchExperimentService,
            ExcelExportService excelExportService
    ) {
        this.dashboardService = dashboardService;
        this.llmExperimentRunnerService = llmExperimentRunnerService;
        this.datasetImportService = datasetImportService;
        this.batchExperimentService = batchExperimentService;
        this.excelExportService = excelExportService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("summary", dashboardService.getSummary());
        model.addAttribute("reels", dashboardService.findAllReels());
        model.addAttribute("models", dashboardService.findModelOptions());
        model.addAttribute("techniques", dashboardService.findPromptTechniqueOptions());
        model.addAttribute("batchStatus", batchExperimentService.getStatus());
        model.addAttribute("modelProcessingTimes", dashboardService.findModelProcessingSummaries());
        return "dashboard";
    }

    @PostMapping("/data/sync")
    public String syncData(RedirectAttributes redirectAttributes) {
        try {
            var summary = datasetImportService.syncDataset();
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Data sync complete: " + summary.audioFilesSynced() + " audio files and "
                            + summary.transcriptsSynced() + " transcripts."
            );
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Data sync failed: " + ex.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/experiments/batch")
    public String runBatch(
            @RequestParam int modelId,
            @RequestParam int techniqueId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            boolean started = batchExperimentService.start(modelId, techniqueId);
            if (started) {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "Batch started. The selected model and prompt will run one transcript at a time."
                );
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "A batch is already running.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Batch could not start: " + ex.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/exports/llm-results.xlsx")
    public ResponseEntity<byte[]> downloadLlmResults() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            excelExportService.writeLlmResults(output);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"));
            String filename = "masakgramprompt-results-" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    ))
                    .body(output.toByteArray());
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Excel export failed. Update the Maven project in Eclipse, restart the dashboard, and try again.",
                    ex
            );
        }
    }

    @GetMapping("/reels/{reelId}")
    public String reelDetail(@PathVariable int reelId, Model model) {
        var reel = dashboardService.findReelById(reelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reel not found"));

        model.addAttribute("reel", reel);
        model.addAttribute("audio", dashboardService.findAudioByReelId(reelId).orElse(null));
        model.addAttribute("transcript", dashboardService.findTranscriptByReelId(reelId).orElse(null));
        model.addAttribute("experiments", dashboardService.findExperimentsByReelId(reelId));
        model.addAttribute("nutritionResults", dashboardService.findNutritionResultsByReelId(reelId));
        model.addAttribute("ingredientResults", dashboardService.findIngredientResultsByReelId(reelId));
        model.addAttribute("models", dashboardService.findModelOptions());
        model.addAttribute("techniques", dashboardService.findPromptTechniqueOptions());

        return "reel-detail";
    }

    @PostMapping("/reels/{reelId}/experiments/run")
    public String runExperiment(
            @PathVariable int reelId,
            @RequestParam int modelId,
            @RequestParam int techniqueId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            int experimentId = llmExperimentRunnerService.run(reelId, modelId, techniqueId);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Experiment " + experimentId + " completed. Previous result for the same model and prompt was replaced."
            );
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/reels/" + reelId + "#experiments";
    }
}
