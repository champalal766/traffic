package com.vegamcity.traffic;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class TrafficController {
    private final TrafficService trafficService;

    public TrafficController(TrafficService trafficService) {
        this.trafficService = trafficService;
    }

    @GetMapping("/dashboard")
    public TrafficService.DashboardResponse dashboard() {
        return trafficService.dashboard();
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return trafficService.subscribe();
    }

    @PutMapping("/traffic")
    public TrafficService.DashboardResponse updateTraffic(
            @Valid @RequestBody TrafficService.TrafficUpdateRequest request) {
        return trafficService.updateTraffic(request);
    }

    @PostMapping("/emergency")
    public TrafficService.DispatchResult dispatch(
            @Valid @RequestBody TrafficService.EmergencyRequest request) {
        return trafficService.dispatch(request);
    }

    @PostMapping("/traffic/resume")
    public TrafficService.DashboardResponse resumeTraffic() {
        return trafficService.resumeTraffic();
    }

    @PostMapping("/route")
    public TrafficService.RouteResult route(
            @Valid @RequestBody TrafficService.RouteRequest request) {
        return trafficService.route(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    public record ErrorResponse(String error) {}
}
