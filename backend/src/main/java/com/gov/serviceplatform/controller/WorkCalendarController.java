package com.gov.serviceplatform.controller;

import com.gov.serviceplatform.entity.WorkCalendar;
import com.gov.serviceplatform.service.WorkCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/work-calendar")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class WorkCalendarController {

    private final WorkCalendarService workCalendarService;

    @GetMapping("/check")
    public ResponseEntity<Boolean> isWorkDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(workCalendarService.isWorkDay(date));
    }

    @GetMapping("/holiday/check")
    public ResponseEntity<Boolean> isHoliday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(workCalendarService.isHoliday(date));
    }

    @GetMapping("/workdays/count")
    public ResponseEntity<Long> countWorkDaysBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(workCalendarService.countWorkDaysBetween(startDate, endDate));
    }

    @GetMapping("/workdays/list")
    public ResponseEntity<List<LocalDate>> getWorkDaysBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(workCalendarService.getWorkDaysBetween(startDate, endDate));
    }

    @PostMapping("/init/{year}")
    public ResponseEntity<Void> initializeYearCalendar(@PathVariable int year) {
        workCalendarService.initializeYearCalendar(year);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/holiday")
    public ResponseEntity<Void> setHoliday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String holidayName) {
        workCalendarService.setHoliday(date, holidayName != null ? holidayName : "节假日");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/workday")
    public ResponseEntity<Void> setWorkDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        workCalendarService.setWorkDay(date);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/holidays/batch")
    public ResponseEntity<Void> setHolidaysBatch(
            @RequestBody List<HolidaySetting> holidays) {
        for (HolidaySetting holiday : holidays) {
            workCalendarService.setHoliday(holiday.getDate(), holiday.getName());
        }
        return ResponseEntity.ok().build();
    }

    @lombok.Data
    public static class HolidaySetting {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate date;
        private String name;
    }
}
