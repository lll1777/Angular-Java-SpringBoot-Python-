package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.Department;
import com.gov.serviceplatform.entity.DepartmentEfficiency;
import com.gov.serviceplatform.entity.Ticket;
import com.gov.serviceplatform.enums.AlertLevel;
import com.gov.serviceplatform.enums.TicketStatus;
import com.gov.serviceplatform.repository.DepartmentEfficiencyRepository;
import com.gov.serviceplatform.repository.DepartmentRepository;
import com.gov.serviceplatform.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EfficiencyService {

    private final DepartmentEfficiencyRepository efficiencyRepository;
    private final DepartmentRepository departmentRepository;
    private final TicketRepository ticketRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void calculateDailyEfficiency() {
        log.info("开始计算部门效能统计: {}", LocalDateTime.now());
        
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        
        List<Department> departments = departmentRepository.findByIsActiveTrue();
        
        for (Department dept : departments) {
            calculateAndSaveEfficiency(dept, yesterday);
        }
        
        updateRanking(yesterday);
        
        log.info("部门效能统计计算完成");
    }

    @Transactional
    public DepartmentEfficiency calculateAndSaveEfficiency(Department department, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        
        DepartmentEfficiency efficiency = efficiencyRepository
            .findByDepartmentIdAndStatisticsDate(department.getId(), date)
            .orElse(new DepartmentEfficiency());
        
        efficiency.setDepartment(department);
        efficiency.setStatisticsDate(date);
        
        List<Ticket> tickets = ticketRepository.findByCreatedAtBetween(startOfDay, endOfDay)
            .stream()
            .filter(t -> t.getCurrentDepartment() != null && 
                        t.getCurrentDepartment().getId().equals(department.getId()))
            .collect(Collectors.toList());
        
        efficiency.setTotalReceived(tickets.size());
        
        long completedCount = tickets.stream()
            .filter(t -> t.getStatus() == TicketStatus.COMPLETED || 
                        t.getStatus() == TicketStatus.CLOSED)
            .count();
        efficiency.setTotalCompleted((int) completedCount);
        
        long overdueCount = tickets.stream()
            .filter(t -> t.getAlertLevel() == AlertLevel.OVERDUE)
            .count();
        efficiency.setTotalOverdue((int) overdueCount);
        
        long redCount = tickets.stream()
            .filter(t -> t.getAlertLevel() == AlertLevel.RED_WARNING)
            .count();
        efficiency.setTotalRedWarning((int) redCount);
        
        long yellowCount = tickets.stream()
            .filter(t -> t.getAlertLevel() == AlertLevel.YELLOW_WARNING)
            .count();
        efficiency.setTotalYellowWarning((int) yellowCount);
        
        Double avgHours = ticketRepository.findAverageProcessingHoursByDepartment(department.getId());
        efficiency.setAverageProcessingHours(avgHours);
        
        Double avgSatisfaction = ticketRepository.findAverageSatisfactionByDepartment(department.getId());
        efficiency.setAverageSatisfactionScore(avgSatisfaction);
        
        double onTimeRate = efficiency.getTotalReceived() > 0 
            ? (double) (efficiency.getTotalReceived() - efficiency.getTotalOverdue()) / efficiency.getTotalReceived() * 100 
            : 100.0;
        efficiency.setOnTimeCompletionRate(onTimeRate);
        
        double satisfactionRate = efficiency.getAverageSatisfactionScore() != null 
            ? efficiency.getAverageSatisfactionScore() * 20 
            : 0.0;
        efficiency.setSatisfactionRate(satisfactionRate);
        
        double efficiencyScore = calculateEfficiencyScore(efficiency);
        efficiency.setEfficiencyScore(efficiencyScore);
        
        return efficiencyRepository.save(efficiency);
    }

    private double calculateEfficiencyScore(DepartmentEfficiency efficiency) {
        double score = 100.0;
        
        score -= efficiency.getTotalOverdue() * 10;
        score -= efficiency.getTotalRedWarning() * 5;
        score -= efficiency.getTotalYellowWarning() * 2;
        
        if (efficiency.getOnTimeCompletionRate() != null) {
            score += (efficiency.getOnTimeCompletionRate() - 80) * 0.5;
        }
        
        if (efficiency.getSatisfactionRate() != null) {
            score += (efficiency.getSatisfactionRate() - 60) * 0.3;
        }
        
        return Math.max(0, Math.min(100, score));
    }

    @Transactional
    public void updateRanking(LocalDate date) {
        List<DepartmentEfficiency> efficiencies = efficiencyRepository
            .findByStatisticsDateOrderByRankAsc(date);
        
        efficiencies.sort((a, b) -> {
            double scoreA = a.getEfficiencyScore() != null ? a.getEfficiencyScore() : 0;
            double scoreB = b.getEfficiencyScore() != null ? b.getEfficiencyScore() : 0;
            return Double.compare(scoreB, scoreA);
        });
        
        for (int i = 0; i < efficiencies.size(); i++) {
            efficiencies.get(i).setRank(i + 1);
            efficiencyRepository.save(efficiencies.get(i));
        }
    }

    public List<DepartmentEfficiency> getDailyRanking(LocalDate date) {
        return efficiencyRepository.findByStatisticsDateOrderByRankAsc(date);
    }

    public List<DepartmentEfficiency> getDepartmentTrend(Long departmentId, LocalDate startDate, LocalDate endDate) {
        return efficiencyRepository.findByDepartmentAndDateRange(departmentId, startDate, endDate);
    }

    public Double getAverageEfficiency(Long departmentId, LocalDate startDate, LocalDate endDate) {
        return efficiencyRepository.findAverageEfficiencyScore(departmentId, startDate, endDate);
    }
}
