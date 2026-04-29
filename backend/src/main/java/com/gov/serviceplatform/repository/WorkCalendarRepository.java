package com.gov.serviceplatform.repository;

import com.gov.serviceplatform.entity.WorkCalendar;
import com.gov.serviceplatform.enums.HolidayType;
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
    
    List<WorkCalendar> findByYear(Integer year);
    
    List<WorkCalendar> findByYearAndMonth(Integer year, Integer month);
    
    List<WorkCalendar> findByDateBetween(LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT wc FROM WorkCalendar wc WHERE wc.date BETWEEN :startDate AND :endDate AND wc.isWorkDay = :isWorkDay")
    List<WorkCalendar> findByDateBetweenAndIsWorkDay(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("isWorkDay") Boolean isWorkDay);
    
    @Query("SELECT COUNT(wc) FROM WorkCalendar wc WHERE wc.date BETWEEN :startDate AND :endDate AND wc.isWorkDay = true")
    long countWorkDaysBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT wc FROM WorkCalendar wc WHERE wc.date = :date OR (wc.holidayType = :holidayType AND wc.isWorkDay = false)")
    Optional<WorkCalendar> findByDateOrHolidayType(@Param("date") LocalDate date, @Param("holidayType") HolidayType holidayType);
    
    boolean existsByDate(LocalDate date);
}
