package com.cgpa.web;

import com.cgpa.model.Models.*;
import com.cgpa.service.ApiException;
import com.cgpa.service.CalculationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final CalculationService service;

    public ApiController(CalculationService service) {
        this.service = service;
    }

    @GetMapping("/courses")
    public List<Course> courses(@RequestParam int semester) {
        return service.courses(semester);
    }

    @GetMapping("/grades")
    public List<GradePoint> grades() {
        return service.gradeScale();
    }

    @PostMapping("/calculate")
    public CalculationResponse calculate(@RequestBody CalculationRequest request) {
        return service.calculate(request);
    }

    @GetMapping("/history")
    public List<HistoryRow> history(@RequestParam(required = false) String roll,
                                    @RequestParam(defaultValue = "100") int limit) {
        return service.history(roll, limit);
    }

    @GetMapping("/history/{id}")
    public HistoryDetail historyDetail(@PathVariable long id) {
        return service.historyDetail(id);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Something went wrong on the server. Check the server log."));
    }
}
