package my.utem.ftmk.masakgramprompt.controller;

import my.utem.ftmk.masakgramprompt.model.Reel;
import my.utem.ftmk.masakgramprompt.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reels")
public class ReelApiController {

    private final DashboardService dashboardService;

    public ReelApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public List<Reel> getAllReels() {
        return dashboardService.findAllReels();
    }
}
