package com.gov.serviceplatform.controller;

import com.gov.serviceplatform.entity.DepartmentEfficiency;
import com.gov.serviceplatform.service.EfficiencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/efficiency")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class EfficiencyController {

    private final EfficiencyService efficiencyService;

    @GetMapping("/ranking")
    public ResponseEntity<List<DepartmentEfficiency>> getDailyRanking(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        List<DepartmentEfficiency> ranking = efficiencyService.getDailyRanking(date);
        return ResponseEntity.ok(ranking);
    }

    @GetMapping("/department/{departmentId}/trend")
    public ResponseEntity<List<DepartmentEfficiency>> getDepartmentTrend(
            @PathVariable Long departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<DepartmentEfficiency> trend = efficiencyService.getDepartmentTrend(departmentId, startDate, endDate);
        return ResponseEntity.ok(trend);
    }

    @GetMapping("/department/{departmentId}/average")
    public ResponseEntity<Double> getAverageEfficiency(
            @PathVariable Long departmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Double avg = efficiencyService.getAverageEfficiency(departmentId, startDate, endDate);
        return ResponseEntity.ok(avg != null ? avg : 0.0);
    }

    @PostMapping("/calculate")
    public ResponseEntity<Void> calculateTodayEfficiency() {
        efficiencyService.calculateDailyEfficiency();
        return ResponseEntity.ok().build();
    }
}
