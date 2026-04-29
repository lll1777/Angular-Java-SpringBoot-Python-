package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.WorkCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, Long> {
    
    Optional<WorkCalendar> findByDate(LocalDate date);
    
    List<WorkCalendar> findByDateBetween(LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT w FROM WorkCalendar w WHERE w.date BETWEEN :startDate AND :endDate AND w.isWorkday = true AND w.isHoliday = false")
    List<WorkCalendar> findWorkDaysBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COUNT(w) FROM WorkCalendar w WHERE w.date BETWEEN :startDate AND :endDate AND w.isWorkday = true AND w.isHoliday = false")
    long countWorkDaysBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT w FROM WorkCalendar w WHERE w.isHoliday = true AND w.date BETWEEN :startDate AND :endDate")
    List<WorkCalendar> findHolidaysBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM WorkCalendar w WHERE w.date = :date AND w.isWorkday = true AND w.isHoliday = false")
    boolean isWorkDay(@Param("date") LocalDate date);
    
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM WorkCalendar w WHERE w.date = :date AND w.isHoliday = true")
    boolean isHoliday(@Param("date") LocalDate date);
}
