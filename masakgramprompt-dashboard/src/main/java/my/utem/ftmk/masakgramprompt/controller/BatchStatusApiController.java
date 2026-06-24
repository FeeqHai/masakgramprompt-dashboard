package my.utem.ftmk.masakgramprompt.controller;

import my.utem.ftmk.masakgramprompt.model.BatchRunStatus;
import my.utem.ftmk.masakgramprompt.service.BatchExperimentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch-status")
public class BatchStatusApiController {

    private final BatchExperimentService batchExperimentService;

    public BatchStatusApiController(BatchExperimentService batchExperimentService) {
        this.batchExperimentService = batchExperimentService;
    }

    @GetMapping
    public BatchRunStatus getBatchStatus() {
        return batchExperimentService.getStatus();
    }
}
