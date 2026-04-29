package com.gov.serviceplatform.controller;

import com.gov.serviceplatform.entity.TicketCooperation;
import com.gov.serviceplatform.service.TicketCooperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cooperations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class CooperationController {

    private final TicketCooperationService cooperationService;

    @PostMapping
    public ResponseEntity<TicketCooperation> createCooperation(@RequestBody Map<String, Object> request) {
        Long ticketId = request.get("ticketId") != null 
            ? ((Number) request.get("ticketId")).longValue() : null;
        Long cooperationDeptId = request.get("cooperationDeptId") != null 
            ? ((Number) request.get("cooperationDeptId")).longValue() : null;
        String requirement = (String) request.get("requirement");
        Integer processingHours = request.get("processingHours") != null 
            ? ((Number) request.get("processingHours")).intValue() : 24;
        
        if (ticketId == null || cooperationDeptId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        TicketCooperation cooperation = cooperationService.createCooperation(
            null, null, cooperationDeptId, requirement, processingHours);
        
        return ResponseEntity.ok(cooperation);
    }

    @GetMapping("/ticket/{ticketId}")
    public ResponseEntity<List<TicketCooperation>> getCooperationsForTicket(@PathVariable Long ticketId) {
        List<TicketCooperation> cooperations = cooperationService.getCooperationsForTicket(ticketId);
        return ResponseEntity.ok(cooperations);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketCooperation> getCooperation(@PathVariable Long id) {
        TicketCooperation cooperation = cooperationService.getCooperationById(id);
        return ResponseEntity.ok(cooperation);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<TicketCooperation> acceptCooperation(@PathVariable Long id) {
        TicketCooperation cooperation = cooperationService.acceptCooperation(id, null);
        return ResponseEntity.ok(cooperation);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TicketCooperation> completeCooperation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String response = body != null ? body.get("response") : null;
        TicketCooperation cooperation = cooperationService.completeCooperation(id, response, null);
        return ResponseEntity.ok(cooperation);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<TicketCooperation> rejectCooperation(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        TicketCooperation cooperation = cooperationService.rejectCooperation(id, reason, null);
        return ResponseEntity.ok(cooperation);
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<TicketCooperation>> getCooperationsForDepartment(
            @PathVariable Long departmentId) {
        List<TicketCooperation> cooperations = cooperationService.getCooperationsForDepartment(departmentId);
        return ResponseEntity.ok(cooperations);
    }
}
