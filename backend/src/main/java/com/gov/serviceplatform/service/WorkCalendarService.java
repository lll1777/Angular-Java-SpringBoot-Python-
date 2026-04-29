package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.WorkCalendar;
import com.gov.serviceplatform.repository.WorkCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final WorkCalendarRepository workCalendarRepository;
    
    private static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORK_END = LocalTime.of(17, 0);
    private static final int DEFAULT_WORK_HOURS_PER_DAY = 8;

    public boolean isWorkDay(LocalDate date) {
        return workCalendarRepository.isWorkDay(date);
    }

    public boolean isHoliday(LocalDate date) {
        return workCalendarRepository.isHoliday(date);
    }

    public LocalDateTime calculateDueTime(LocalDateTime startTime, int processingHours, boolean useWorkDays) {
        if (useWorkDays) {
            return calculateDueTimeUsingWorkDays(startTime, processingHours);
        } else {
            return startTime.plusHours(processingHours);
        }
    }

    private LocalDateTime calculateDueTimeUsingWorkDays(LocalDateTime startTime, int processingHours) {
        LocalDateTime currentTime = adjustToWorkTime(startTime);
        int remainingHours = processingHours;
        
        while (remainingHours > 0) {
            LocalDate currentDate = currentTime.toLocalDate();
            
            if (!isWorkDay(currentDate)) {
                currentTime = getNextWorkDayStart(currentDate);
                continue;
            }
            
            WorkCalendar workDay = workCalendarRepository.findByDate(currentDate).orElse(null);
            LocalTime workStart = workDay != null && workDay.getWorkStartTime() != null 
                ? LocalTime.parse(workDay.getWorkStartTime()) 
                : DEFAULT_WORK_START;
            LocalTime workEnd = workDay != null && workDay.getWorkEndTime() != null 
                ? LocalTime.parse(workDay.getWorkEndTime()) 
                : DEFAULT_WORK_END;
            
            int workHoursToday = (int) java.time.Duration.between(workStart, workEnd).toHours();
            
            LocalTime currentTimeOfDay = currentTime.toLocalTime();
            
            if (currentTimeOfDay.isBefore(workStart)) {
                currentTime = LocalDateTime.of(currentDate, workStart);
                currentTimeOfDay = workStart;
            }
            
            if (currentTimeOfDay.isAfter(workEnd)) {
                currentTime = getNextWorkDayStart(currentDate);
                continue;
            }
            
            long hoursLeftToday = java.time.Duration.between(currentTimeOfDay, workEnd).toHours();
            
            if (remainingHours <= hoursLeftToday) {
                currentTime = currentTime.plusHours(remainingHours);
                remainingHours = 0;
            } else {
                remainingHours -= hoursLeftToday;
                currentTime = getNextWorkDayStart(currentDate);
            }
        }
        
        return currentTime;
    }

    private LocalDateTime adjustToWorkTime(LocalDateTime time) {
        LocalDate date = time.toLocalDate();
        LocalTime timeOfDay = time.toLocalTime();
        
        if (!isWorkDay(date)) {
            return getNextWorkDayStart(date);
        }
        
        WorkCalendar workDay = workCalendarRepository.findByDate(date).orElse(null);
        LocalTime workStart = workDay != null && workDay.getWorkStartTime() != null 
            ? LocalTime.parse(workDay.getWorkStartTime()) 
            : DEFAULT_WORK_START;
        LocalTime workEnd = workDay != null && workDay.getWorkEndTime() != null 
            ? LocalTime.parse(workDay.getWorkEndTime()) 
            : DEFAULT_WORK_END;
        
        if (timeOfDay.isBefore(workStart)) {
            return LocalDateTime.of(date, workStart);
        }
        
        if (timeOfDay.isAfter(workEnd)) {
            return getNextWorkDayStart(date);
        }
        
        return time;
    }

    private LocalDateTime getNextWorkDayStart(LocalDate fromDate) {
        LocalDate nextDate = fromDate.plusDays(1);
        while (!isWorkDay(nextDate)) {
            nextDate = nextDate.plusDays(1);
        }
        
        WorkCalendar workDay = workCalendarRepository.findByDate(nextDate).orElse(null);
        LocalTime workStart = workDay != null && workDay.getWorkStartTime() != null 
            ? LocalTime.parse(workDay.getWorkStartTime()) 
            : DEFAULT_WORK_START;
        
        return LocalDateTime.of(nextDate, workStart);
    }

    public int calculateRemainingWorkHours(LocalDateTime now, LocalDateTime dueTime) {
        if (now.isAfter(dueTime)) {
            return 0;
        }
        
        int remainingHours = 0;
        LocalDateTime currentTime = adjustToWorkTime(now);
        
        while (currentTime.isBefore(dueTime)) {
            LocalDate currentDate = currentTime.toLocalDate();
            
            if (!isWorkDay(currentDate)) {
                currentTime = getNextWorkDayStart(currentDate);
                continue;
            }
            
            WorkCalendar workDay = workCalendarRepository.findByDate(currentDate).orElse(null);
            LocalTime workStart = workDay != null && workDay.getWorkStartTime() != null 
                ? LocalTime.parse(workDay.getWorkStartTime()) 
                : DEFAULT_WORK_START;
            LocalTime workEnd = workDay != null && workDay.getWorkEndTime() != null 
                ? LocalTime.parse(workDay.getWorkEndTime()) 
                : DEFAULT_WORK_END;
            
            LocalTime currentTimeOfDay = currentTime.toLocalTime();
            LocalTime endTimeOfDay = dueTime.toLocalDate().isEqual(currentDate) 
                ? dueTime.toLocalTime() 
                : workEnd;
            
            if (currentTimeOfDay.isBefore(workStart)) {
                currentTimeOfDay = workStart;
            }
            
            if (currentTimeOfDay.isAfter(workEnd)) {
                currentTime = getNextWorkDayStart(currentDate);
                continue;
            }
            
            long hours = java.time.Duration.between(currentTimeOfDay, 
                endTimeOfDay.isBefore(workEnd) ? endTimeOfDay : workEnd).toHours();
            
            remainingHours += Math.max(0, hours);
            
            currentTime = getNextWorkDayStart(currentDate);
        }
        
        return remainingHours;
    }

    public long countWorkDaysBetween(LocalDate startDate, LocalDate endDate) {
        return workCalendarRepository.countWorkDaysBetween(startDate, endDate);
    }

    public List<LocalDate> getWorkDaysBetween(LocalDate startDate, LocalDate endDate) {
        List<WorkCalendar> workDays = workCalendarRepository.findWorkDaysBetween(startDate, endDate);
        return workDays.stream().map(WorkCalendar::getDate).toList();
    }

    @Transactional
    public void initializeYearCalendar(int year) {
        log.info("初始化 {} 年工作日历", year);
        
        List<WorkCalendar> calendars = new ArrayList<>();
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            WorkCalendar calendar = new WorkCalendar();
            calendar.setDate(date);
            calendar.setDayOfWeek(date.getDayOfWeek().getValue());
            
            boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || 
                               date.getDayOfWeek() == DayOfWeek.SUNDAY;
            
            calendar.setIsWorkday(!isWeekend);
            calendar.setIsHoliday(false);
            calendar.setWorkStartTime("09:00");
            calendar.setWorkEndTime("17:00");
            
            calendars.add(calendar);
        }
        
        workCalendarRepository.saveAll(calendars);
        log.info("完成 {} 年工作日历初始化，共 {} 天", year, calendars.size());
    }

    @Transactional
    public void setHoliday(LocalDate date, String holidayName) {
        WorkCalendar calendar = workCalendarRepository.findByDate(date).orElseGet(() -> {
            WorkCalendar newCal = new WorkCalendar();
            newCal.setDate(date);
            newCal.setDayOfWeek(date.getDayOfWeek().getValue());
            newCal.setWorkStartTime("09:00");
            newCal.setWorkEndTime("17:00");
            return newCal;
        });
        
        calendar.setIsHoliday(true);
        calendar.setIsWorkday(false);
        calendar.setHolidayName(holidayName);
        
        workCalendarRepository.save(calendar);
        log.info("设置节假日: {} - {}", date, holidayName);
    }

    @Transactional
    public void setWorkDay(LocalDate date) {
        WorkCalendar calendar = workCalendarRepository.findByDate(date).orElseGet(() -> {
            WorkCalendar newCal = new WorkCalendar();
            newCal.setDate(date);
            newCal.setDayOfWeek(date.getDayOfWeek().getValue());
            newCal.setWorkStartTime("09:00");
            newCal.setWorkEndTime("17:00");
            return newCal;
        });
        
        calendar.setIsWorkday(true);
        calendar.setIsHoliday(false);
        
        workCalendarRepository.save(calendar);
        log.info("设置工作日: {}", date);
    }
}
