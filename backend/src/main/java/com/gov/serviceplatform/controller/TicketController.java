package com.gov.serviceplatform.controller;

import com.gov.serviceplatform.dto.TicketCreateDTO;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.entity.TicketCooperation;
import com.gov.serviceplatform.entity.TicketFlow;
import com.gov.serviceplatform.entity.TicketRoutingHistory;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.TicketFlowRepository;
import com.gov.serviceplatform.service.AuditService;
import com.gov.serviceplatform.service.TicketService;
import com.gov.serviceplatform.service.ai.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class TicketController {

    private final TicketService ticketService;
    private final TicketFlowRepository ticketFlowRepository;
    private final AIService aiService;
    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<Ticket> createTicket(@RequestBody TicketCreateDTO dto) {
        Ticket ticket = ticketService.createTicket(dto, null);
        return ResponseEntity.ok(ticket);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ticket> getTicketById(@PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id);
        return ResponseEntity.ok(ticket);
    }

    @GetMapping("/number/{ticketNumber}")
    public ResponseEntity<Ticket> getTicketByNumber(@PathVariable String ticketNumber) {
        Ticket ticket = ticketService.getTicketByNumber(ticketNumber);
        return ResponseEntity.ok(ticket);
    }

    @GetMapping
    public ResponseEntity<Page<Ticket>> getAllTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Ticket> tickets = ticketService.queryTickets(null, 
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/citizen/{citizenId}")
    public ResponseEntity<Page<Ticket>> getTicketsByCitizen(
            @PathVariable Long citizenId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Ticket> tickets = ticketService.getTicketsByCitizen(citizenId,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<Page<Ticket>> getTicketsByDepartment(
            @PathVariable Long departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Ticket> tickets = ticketService.getTicketsByDepartment(departmentId,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<Ticket>> getTicketsByStatus(
            @PathVariable TicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Ticket> tickets = ticketService.getTicketsByStatus(status,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(tickets);
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Ticket> assignTicket(
            @PathVariable Long id,
            @RequestParam(required = false) Long departmentId) {
        Ticket ticket = ticketService.assignTicket(id, departmentId, null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Ticket> acceptTicket(@PathVariable Long id) {
        Ticket ticket = ticketService.acceptTicket(id, null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<Ticket> startProcessing(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String content = body != null ? body.get("content") : null;
        Ticket ticket = ticketService.startProcessing(id, content, null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/transfer")
    public ResponseEntity<Ticket> transferTicket(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long targetDepartmentId = body.get("targetDepartmentId") != null 
            ? ((Number) body.get("targetDepartmentId")).longValue() : null;
        String reason = (String) body.get("reason");
        Ticket ticket = ticketService.transferTicket(id, targetDepartmentId, reason, null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<Ticket> returnTicket(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        Ticket ticket = ticketService.returnTicket(id, reason, null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/escalate")
    public ResponseEntity<Ticket> escalateTicket(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        Ticket ticket = ticketService.escalateTicket(id, reason, null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/cooperate")
    public ResponseEntity<TicketCooperation> createCooperation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long cooperationDeptId = body.get("cooperationDeptId") != null 
            ? ((Number) body.get("cooperationDeptId")).longValue() : null;
        String requirement = (String) body.get("requirement");
        Integer processingHours = body.get("processingHours") != null 
            ? ((Number) body.get("processingHours")).intValue() : 24;
        
        TicketCooperation cooperation = ticketService.createCooperation(
            id, cooperationDeptId, requirement, processingHours, null);
        return ResponseEntity.ok(cooperation);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Ticket> completeTicket(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String content = body != null ? body.get("content") : null;
        Ticket ticket = ticketService.completeTicket(id, content, null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Ticket> closeTicket(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Integer satisfaction = body != null && body.get("satisfaction") != null 
            ? ((Number) body.get("satisfaction")).intValue() : null;
        String comment = body != null ? (String) body.get("comment") : null;
        Ticket ticket = ticketService.closeTicket(id, satisfaction, comment, null);
        return ResponseEntity.ok(ticket);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Ticket> cancelTicket(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        Ticket ticket = ticketService.cancelTicket(id, reason, null);
        return ResponseEntity.ok(ticket);
    }

    @GetMapping("/{id}/flows")
    public ResponseEntity<List<TicketFlow>> getTicketFlows(@PathVariable Long id) {
        List<TicketFlow> flows = ticketFlowRepository.findByTicketIdOrderByCreatedAtAsc(id);
        return ResponseEntity.ok(flows);
    }

    @GetMapping("/{id}/routing-history")
    public ResponseEntity<List<TicketRoutingHistory>> getTicketRoutingHistory(@PathVariable Long id) {
        List<TicketRoutingHistory> history = ticketService.getTicketRoutingHistory(id);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/cooperations")
    public ResponseEntity<List<TicketCooperation>> getTicketCooperations(@PathVariable Long id) {
        List<TicketCooperation> cooperations = ticketService.getTicketCooperations(id);
        return ResponseEntity.ok(cooperations);
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<List<AIService.SimilarTicketResult>> getSimilarTickets(@PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id);
        List<AIService.SimilarTicketResult> similar = aiService.findSimilarTickets(
            id, ticket.getTitle() + " " + ticket.getContent(), 5);
        return ResponseEntity.ok(similar);
    }

    @GetMapping("/{id}/knowledge")
    public ResponseEntity<List<AIService.KnowledgeResult>> getRecommendedKnowledge(@PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id);
        List<AIService.KnowledgeResult> knowledge = aiService.recommendKnowledge(
            ticket.getTitle() + " " + ticket.getContent(), 5);
        return ResponseEntity.ok(knowledge);
    }

    @PostMapping("/batch-assign")
    public ResponseEntity<AIService.BatchAssignResult> batchAssign(@RequestBody List<Map<String, String>> tickets) {
        List<String> contents = tickets.stream()
            .map(t -> t.get("title") + " " + t.getOrDefault("content", ""))
            .toList();
        AIService.BatchAssignResult result = aiService.batchClassifyAndAssign(contents);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<?>> getAuditLogs(@PathVariable Long id) {
        var logs = auditService.getAllLogsByTarget("Ticket", id);
        return ResponseEntity.ok(logs);
    }
}
