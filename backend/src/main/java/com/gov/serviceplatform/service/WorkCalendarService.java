package com.gov.serviceplatform.service;

import com.gov.serviceplatform.entity.WorkCalendar;
import com.gov.serviceplatform.enums.HolidayType;
import com.gov.serviceplatform.repository.WorkCalendarRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final WorkCalendarRepository workCalendarRepository;
    
    private static final Map<DayOfWeek, Boolean> DEFAULT_WORK_DAYS = new EnumMap<>(DayOfWeek.class);
    
    static {
        DEFAULT_WORK_DAYS.put(DayOfWeek.MONDAY, true);
        DEFAULT_WORK_DAYS.put(DayOfWeek.TUESDAY, true);
        DEFAULT_WORK_DAYS.put(DayOfWeek.WEDNESDAY, true);
        DEFAULT_WORK_DAYS.put(DayOfWeek.THURSDAY, true);
        DEFAULT_WORK_DAYS.put(DayOfWeek.FRIDAY, true);
        DEFAULT_WORK_DAYS.put(DayOfWeek.SATURDAY, false);
        DEFAULT_WORK_DAYS.put(DayOfWeek.SUNDAY, false);
    }

    private static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORK_END = LocalTime.of(18, 0);
    private static final LocalTime DEFAULT_LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime DEFAULT_LUNCH_END = LocalTime.of(13, 30);

    @PostConstruct
    public void init() {
        initializeCurrentYearCalendar();
    }

    @Transactional
    public void initializeCurrentYearCalendar() {
        int currentYear = LocalDate.now().getYear();
        initializeYearCalendar(currentYear);
        initializeYearCalendar(currentYear + 1);
    }

    @Transactional
    public void initializeYearCalendar(int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        
        long existingCount = workCalendarRepository.count();
        if (existingCount > 0) {
            return;
        }

        log.info("初始化 {} 年工作日历", year);
        
        List<WorkCalendar> calendars = new ArrayList<>();
        LocalDate date = startDate;
        
        while (!date.isAfter(endDate)) {
            WorkCalendar calendar = createDefaultCalendar(date);
            calendars.add(calendar);
            date = date.plusDays(1);
        }
        
        applyFixedHolidays(year, calendars);
        
        workCalendarRepository.saveAll(calendars);
        log.info("成功初始化 {} 天工作日历记录", calendars.size());
    }

    private WorkCalendar createDefaultCalendar(LocalDate date) {
        WorkCalendar calendar = new WorkCalendar();
        calendar.setDate(date);
        calendar.setYear(date.getYear());
        calendar.setMonth(date.getMonthValue());
        calendar.setDayOfWeek(date.getDayOfWeek().getValue());
        
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWorkDay = DEFAULT_WORK_DAYS.getOrDefault(dayOfWeek, true);
        
        calendar.setIsWorkDay(isWorkDay);
        calendar.setHolidayType(isWorkDay ? null : HolidayType.WEEKEND);
        calendar.setWorkStartTime(DEFAULT_WORK_START);
        calendar.setWorkEndTime(DEFAULT_WORK_END);
        calendar.setLunchStartTime(DEFAULT_LUNCH_START);
        calendar.setLunchEndTime(DEFAULT_LUNCH_END);
        
        return calendar;
    }

    private void applyFixedHolidays(int year, List<WorkCalendar> calendars) {
        Map<String, HolidayType> fixedHolidays = new HashMap<>();
        
        fixedHolidays.put(year + "-01-01", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-01-28", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-01-29", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-01-30", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-01-31", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-02-01", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-02-02", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-04-04", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-05-01", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-05-02", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-05-03", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-05-04", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-05-05", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-06-22", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-06-23", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-06-24", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-10-01", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-10-02", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-10-03", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-10-04", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-10-05", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-10-06", HolidayType.NATIONAL_HOLIDAY);
        fixedHolidays.put(year + "-10-07", HolidayType.NATIONAL_HOLIDAY);
        
        Map<String, HolidayType> workingWeekends = new HashMap<>();
        workingWeekends.put(year + "-02-04", HolidayType.WORKING_WEEKEND);
        workingWeekends.put(year + "-02-18", HolidayType.WORKING_WEEKEND);
        workingWeekends.put(year + "-04-07", HolidayType.WORKING_WEEKEND);
        workingWeekends.put(year + "-04-28", HolidayType.WORKING_WEEKEND);
        workingWeekends.put(year + "-05-11", HolidayType.WORKING_WEEKEND);
        workingWeekends.put(year + "-06-16", HolidayType.WORKING_WEEKEND);
        workingWeekends.put(year + "-09-29", HolidayType.WORKING_WEEKEND);
        workingWeekends.put(year + "-10-12", HolidayType.WORKING_WEEKEND);
        
        for (WorkCalendar calendar : calendars) {
            String dateStr = calendar.getDate().toString();
            
            if (fixedHolidays.containsKey(dateStr)) {
                calendar.setIsWorkDay(false);
                calendar.setHolidayType(fixedHolidays.get(dateStr));
                calendar.setHolidayName(getHolidayName(fixedHolidays.get(dateStr)));
            }
            
            if (workingWeekends.containsKey(dateStr)) {
                calendar.setIsWorkDay(true);
                calendar.setHolidayType(workingWeekends.get(dateStr));
                calendar.setHolidayName("调休上班");
            }
        }
    }

    private String getHolidayName(HolidayType type) {
        return type.getDescription();
    }

    public boolean isWorkDay(LocalDate date) {
        Optional<WorkCalendar> calendarOpt = workCalendarRepository.findByDate(date);
        
        if (calendarOpt.isPresent()) {
            return calendarOpt.get().getIsWorkDay();
        }
        
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return DEFAULT_WORK_DAYS.getOrDefault(dayOfWeek, true);
    }

    public WorkCalendar getWorkCalendar(LocalDate date) {
        return workCalendarRepository.findByDate(date)
            .orElseGet(() -> {
                WorkCalendar calendar = createDefaultCalendar(date);
                return calendar;
            });
    }

    public LocalDateTime addWorkHours(LocalDateTime startDateTime, long hoursToAdd) {
        if (hoursToAdd <= 0) {
            return startDateTime;
        }

        LocalDateTime current = startDateTime;
        long remainingHours = hoursToAdd;

        while (remainingHours > 0) {
            WorkCalendar calendar = getWorkCalendar(current.toLocalDate());
            
            if (!calendar.getIsWorkDay()) {
                current = current.toLocalDate().plusDays(1).atTime(calendar.getWorkStartTime());
                continue;
            }

            LocalTime workStart = calendar.getWorkStartTime();
            LocalTime workEnd = calendar.getWorkEndTime();
            LocalTime lunchStart = calendar.getLunchStartTime();
            LocalTime lunchEnd = calendar.getLunchEndTime();

            LocalTime currentTime = current.toLocalTime();
            
            if (currentTime.isBefore(workStart)) {
                current = current.toLocalDate().atTime(workStart);
                currentTime = workStart;
            }
            
            if (currentTime.isAfter(workEnd)) {
                current = current.toLocalDate().plusDays(1).atTime(workStart);
                continue;
            }

            double availableHoursToday = calculateAvailableHours(currentTime, workStart, workEnd, lunchStart, lunchEnd);
            
            if (remainingHours <= availableHoursToday) {
                current = addHoursWithinWorkDay(current, remainingHours, workStart, workEnd, lunchStart, lunchEnd);
                remainingHours = 0;
            } else {
                remainingHours -= (long) availableHoursToday;
                current = current.toLocalDate().plusDays(1).atTime(workStart);
            }
        }

        return current;
    }

    private double calculateAvailableHours(LocalTime currentTime, LocalTime workStart, LocalTime workEnd,
                                           LocalTime lunchStart, LocalTime lunchEnd) {
        double totalHours = 0;
        
        if (currentTime.isBefore(lunchStart)) {
            double morningHours = java.time.Duration.between(currentTime, lunchStart).toMinutes() / 60.0;
            totalHours += morningHours;
            double afternoonHours = java.time.Duration.between(lunchEnd, workEnd).toMinutes() / 60.0;
            totalHours += afternoonHours;
        } else if (currentTime.isBefore(lunchEnd)) {
            double afternoonHours = java.time.Duration.between(lunchEnd, workEnd).toMinutes() / 60.0;
            totalHours += afternoonHours;
        } else {
            double afternoonHours = java.time.Duration.between(currentTime, workEnd).toMinutes() / 60.0;
            totalHours += Math.max(0, afternoonHours);
        }
        
        return totalHours;
    }

    private LocalDateTime addHoursWithinWorkDay(LocalDateTime current, double hoursToAdd,
                                                  LocalTime workStart, LocalTime workEnd,
                                                  LocalTime lunchStart, LocalTime lunchEnd) {
        LocalTime currentTime = current.toLocalTime();
        LocalDate currentDate = current.toLocalDate();
        
        long minutesToAdd = (long) (hoursToAdd * 60);
        
        while (minutesToAdd > 0) {
            if (currentTime.isBefore(lunchStart)) {
                long minutesToLunch = java.time.Duration.between(currentTime, lunchStart).toMinutes();
                if (minutesToAdd <= minutesToLunch) {
                    currentTime = currentTime.plusMinutes(minutesToAdd);
                    minutesToAdd = 0;
                } else {
                    minutesToAdd -= minutesToLunch;
                    currentTime = lunchEnd;
                }
            } else if (currentTime.isBefore(lunchEnd)) {
                currentTime = lunchEnd;
            } else {
                long minutesToEnd = java.time.Duration.between(currentTime, workEnd).toMinutes();
                if (minutesToAdd <= minutesToEnd) {
                    currentTime = currentTime.plusMinutes(minutesToAdd);
                    minutesToAdd = 0;
                } else {
                    minutesToAdd -= minutesToEnd;
                    return currentDate.plusDays(1).atTime(workStart).plusMinutes(minutesToAdd);
                }
            }
        }
        
        return currentDate.atTime(currentTime);
    }

    public long calculateWorkHoursBetween(LocalDateTime start, LocalDateTime end) {
        if (end.isBefore(start)) {
            return 0;
        }

        long totalWorkMinutes = 0;
        LocalDateTime current = start;

        while (current.isBefore(end)) {
            WorkCalendar calendar = getWorkCalendar(current.toLocalDate());
            
            if (!calendar.getIsWorkDay()) {
                current = current.toLocalDate().plusDays(1).atTime(calendar.getWorkStartTime());
                if (current.isAfter(end)) {
                    break;
                }
                continue;
            }

            LocalTime workStart = calendar.getWorkStartTime();
            LocalTime workEnd = calendar.getWorkEndTime();
            LocalTime lunchStart = calendar.getLunchStartTime();
            LocalTime lunchEnd = calendar.getLunchEndTime();

            LocalTime currentTime = current.toLocalTime();
            LocalTime endTime = end.toLocalDate().equals(current.toLocalDate()) 
                ? end.toLocalTime() 
                : workEnd;

            if (currentTime.isBefore(workStart)) {
                currentTime = workStart;
            }

            if (currentTime.isAfter(workEnd)) {
                current = current.toLocalDate().plusDays(1).atTime(workStart);
                continue;
            }

            LocalDateTime dayStart = current.toLocalDate().atTime(currentTime);
            LocalDateTime dayEnd = current.toLocalDate().atTime(endTime);
            
            if (dayEnd.isAfter(current.toLocalDate().atTime(workEnd))) {
                dayEnd = current.toLocalDate().atTime(workEnd);
            }

            long dayMinutes = calculateWorkMinutesInDay(dayStart, dayEnd, workStart, workEnd, lunchStart, lunchEnd);
            totalWorkMinutes += dayMinutes;

            current = current.toLocalDate().plusDays(1).atTime(workStart);
        }

        return totalWorkMinutes / 60;
    }

    private long calculateWorkMinutesInDay(LocalDateTime start, LocalDateTime end,
                                             LocalTime workStart, LocalTime workEnd,
                                             LocalTime lunchStart, LocalTime lunchEnd) {
        long totalMinutes = 0;

        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();

        if (startTime.isBefore(lunchStart)) {
            LocalTime effectiveEnd = endTime.isBefore(lunchStart) ? endTime : lunchStart;
            if (startTime.isBefore(workStart)) {
                startTime = workStart;
            }
            if (effectiveEnd.isAfter(startTime)) {
                totalMinutes += java.time.Duration.between(startTime, effectiveEnd).toMinutes();
            }
        }

        if (endTime.isAfter(lunchEnd)) {
            LocalTime effectiveStart = startTime.isAfter(lunchEnd) ? startTime : lunchEnd;
            if (endTime.isAfter(workEnd)) {
                endTime = workEnd;
            }
            if (endTime.isAfter(effectiveStart)) {
                totalMinutes += java.time.Duration.between(effectiveStart, endTime).toMinutes();
            }
        }

        return Math.max(0, totalMinutes);
    }

    public LocalDateTime adjustToWorkDay(LocalDateTime dateTime) {
        WorkCalendar calendar = getWorkCalendar(dateTime.toLocalDate());
        
        if (!calendar.getIsWorkDay()) {
            LocalDate nextWorkDay = dateTime.toLocalDate();
            do {
                nextWorkDay = nextWorkDay.plusDays(1);
            } while (!isWorkDay(nextWorkDay));
            
            WorkCalendar nextCalendar = getWorkCalendar(nextWorkDay);
            return nextWorkDay.atTime(nextCalendar.getWorkStartTime());
        }

        LocalTime time = dateTime.toLocalTime();
        if (time.isBefore(calendar.getWorkStartTime())) {
            return dateTime.toLocalDate().atTime(calendar.getWorkStartTime());
        }
        if (time.isAfter(calendar.getWorkEndTime())) {
            return dateTime.toLocalDate().plusDays(1).atTime(calendar.getWorkStartTime());
        }
        
        return dateTime;
    }

    @Transactional
    public WorkCalendar addCustomHoliday(LocalDate date, HolidayType type, String name, String remark) {
        WorkCalendar calendar = workCalendarRepository.findByDate(date)
            .orElseGet(() -> {
                WorkCalendar newCal = createDefaultCalendar(date);
                return newCal;
            });
        
        calendar.setIsWorkDay(type == HolidayType.WORKING_WEEKEND);
        calendar.setHolidayType(type);
        calendar.setHolidayName(name);
        calendar.setRemark(remark);
        
        return workCalendarRepository.save(calendar);
    }

    public List<WorkCalendar> getHolidaysBetween(LocalDate start, LocalDate end) {
        return workCalendarRepository.findByDateBetweenAndIsWorkDay(start, end, false);
    }
}
