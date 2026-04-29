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
    
    private static final int DEFAULT_WORK_HOURS_PER_DAY = 8;

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
        
        long existingCount = workCalendarRepository.countByDateBetween(startDate, endDate);
        if (existingCount > 0) {
            log.debug("{} 年工作日历已存在，跳过初始化", year);
            return;
        }

        log.info("初始化 {} 年政务标准工作日历", year);
        
        List<WorkCalendar> calendars = new ArrayList<>();
        LocalDate date = startDate;
        
        while (!date.isAfter(endDate)) {
            WorkCalendar calendar = createDefaultCalendar(date);
            calendars.add(calendar);
            date = date.plusDays(1);
        }
        
        applyGovernmentHolidays(year, calendars);
        
        workCalendarRepository.saveAll(calendars);
        log.info("成功初始化 {} 年政务工作日历记录，共 {} 天", year, calendars.size());
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
        calendar.setHolidayName(isWorkDay ? null : (dayOfWeek == DayOfWeek.SATURDAY ? "周六" : "周日"));
        calendar.setWorkStartTime(DEFAULT_WORK_START);
        calendar.setWorkEndTime(DEFAULT_WORK_END);
        calendar.setLunchStartTime(DEFAULT_LUNCH_START);
        calendar.setLunchEndTime(DEFAULT_LUNCH_END);
        calendar.setWorkHoursPerDay(DEFAULT_WORK_HOURS_PER_DAY);
        
        return calendar;
    }

    private void applyGovernmentHolidays(int year, List<WorkCalendar> calendars) {
        Map<String, HolidayInfo> holidays = getGovernmentHolidays(year);
        Map<String, HolidayInfo> workingWeekends = getWorkingWeekends(year);
        
        for (WorkCalendar calendar : calendars) {
            String dateStr = calendar.getDate().toString();
            
            if (holidays.containsKey(dateStr)) {
                HolidayInfo info = holidays.get(dateStr);
                calendar.setIsWorkDay(false);
                calendar.setHolidayType(info.type);
                calendar.setHolidayName(info.name);
                calendar.setRemark(info.remark);
            }
            
            if (workingWeekends.containsKey(dateStr)) {
                HolidayInfo info = workingWeekends.get(dateStr);
                calendar.setIsWorkDay(true);
                calendar.setHolidayType(info.type);
                calendar.setHolidayName(info.name);
                calendar.setRemark(info.remark);
            }
        }
    }

    private Map<String, HolidayInfo> getGovernmentHolidays(int year) {
        Map<String, HolidayInfo> holidays = new HashMap<>();
        
        holidays.put(year + "-01-01", new HolidayInfo(HolidayType.NATIONAL_HOLIDAY, "元旦", "新年第一天"));
        
        int springFestivalStart = getSpringFestivalStart(year);
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.of(year, 2, springFestivalStart).plusDays(i);
            holidays.put(date.toString(), new HolidayInfo(HolidayType.NATIONAL_HOLIDAY, "春节", "农历新年"));
        }
        
        int qingmingDate = getQingmingDate(year);
        holidays.put(year + "-04-" + String.format("%02d", qingmingDate), 
            new HolidayInfo(HolidayType.NATIONAL_HOLIDAY, "清明节", "传统祭扫节日"));
        
        for (int i = 1; i <= 5; i++) {
            holidays.put(year + "-05-" + String.format("%02d", i), 
                new HolidayInfo(HolidayType.NATIONAL_HOLIDAY, "劳动节", "国际劳动节"));
        }
        
        int dragonBoatStart = getDragonBoatStart(year);
        for (int i = 0; i < 3; i++) {
            LocalDate date = LocalDate.of(year, 6, dragonBoatStart).plusDays(i);
            holidays.put(date.toString(), new HolidayInfo(HolidayType.NATIONAL_HOLIDAY, "端午节", "传统节日"));
        }
        
        for (int i = 1; i <= 7; i++) {
            holidays.put(year + "-10-" + String.format("%02d", i), 
                new HolidayInfo(HolidayType.NATIONAL_HOLIDAY, "国庆节", "建国纪念日"));
        }
        
        return holidays;
    }

    private Map<String, HolidayInfo> getWorkingWeekends(int year) {
        Map<String, HolidayInfo> workingWeekends = new HashMap<>();
        
        workingWeekends.put(year + "-02-04", new HolidayInfo(HolidayType.WORKING_WEEKEND, "调休上班", "春节假期调休"));
        workingWeekends.put(year + "-02-18", new HolidayInfo(HolidayType.WORKING_WEEKEND, "调休上班", "春节假期调休"));
        workingWeekends.put(year + "-04-07", new HolidayInfo(HolidayType.WORKING_WEEKEND, "调休上班", "清明节调休"));
        workingWeekends.put(year + "-04-28", new HolidayInfo(HolidayType.WORKING_WEEKEND, "调休上班", "劳动节调休"));
        workingWeekends.put(year + "-05-11", new HolidayInfo(HolidayType.WORKING_WEEKEND, "调休上班", "劳动节调休"));
        workingWeekends.put(year + "-09-29", new HolidayInfo(HolidayType.WORKING_WEEKEND, "调休上班", "国庆节调休"));
        workingWeekends.put(year + "-10-12", new HolidayInfo(HolidayType.WORKING_WEEKEND, "调休上班", "国庆节调休"));
        
        return workingWeekends;
    }

    private int getSpringFestivalStart(int year) {
        return 10;
    }

    private int getQingmingDate(int year) {
        return 4;
    }

    private int getDragonBoatStart(int year) {
        return 22;
    }

    private static class HolidayInfo {
        HolidayType type;
        String name;
        String remark;
        
        HolidayInfo(HolidayType type, String name, String remark) {
            this.type = type;
            this.name = name;
            this.remark = remark;
        }
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
        return addWorkHoursWithMinutes(startDateTime, hoursToAdd * 60);
    }

    public LocalDateTime addWorkHoursWithMinutes(LocalDateTime startDateTime, long minutesToAdd) {
        if (minutesToAdd <= 0) {
            return startDateTime;
        }

        LocalDateTime current = startDateTime;
        long remainingMinutes = minutesToAdd;

        while (remainingMinutes > 0) {
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

            long availableMinutesToday = calculateAvailableMinutes(currentTime, workStart, workEnd, lunchStart, lunchEnd);
            
            if (remainingMinutes <= availableMinutesToday) {
                current = addMinutesWithinWorkDay(current, remainingMinutes, workStart, workEnd, lunchStart, lunchEnd);
                remainingMinutes = 0;
            } else {
                remainingMinutes -= availableMinutesToday;
                current = current.toLocalDate().plusDays(1).atTime(workStart);
            }
        }

        return current;
    }

    public LocalDateTime subtractWorkHours(LocalDateTime startDateTime, long hoursToSubtract) {
        return subtractWorkHoursWithMinutes(startDateTime, hoursToSubtract * 60);
    }

    public LocalDateTime subtractWorkHoursWithMinutes(LocalDateTime startDateTime, long minutesToSubtract) {
        if (minutesToSubtract <= 0) {
            return startDateTime;
        }

        LocalDateTime current = startDateTime;
        long remainingMinutes = minutesToSubtract;

        while (remainingMinutes > 0) {
            WorkCalendar calendar = getWorkCalendar(current.toLocalDate());
            
            if (!calendar.getIsWorkDay()) {
                current = current.toLocalDate().minusDays(1).atTime(calendar.getWorkEndTime());
                continue;
            }

            LocalTime workStart = calendar.getWorkStartTime();
            LocalTime workEnd = calendar.getWorkEndTime();
            LocalTime lunchStart = calendar.getLunchStartTime();
            LocalTime lunchEnd = calendar.getLunchEndTime();

            LocalTime currentTime = current.toLocalTime();
            
            if (currentTime.isAfter(workEnd)) {
                current = current.toLocalDate().atTime(workEnd);
                currentTime = workEnd;
            }
            
            if (currentTime.isBefore(workStart)) {
                current = current.toLocalDate().minusDays(1).atTime(workEnd);
                continue;
            }

            long availableMinutesToday = calculateAvailableMinutesBackward(currentTime, workStart, workEnd, lunchStart, lunchEnd);
            
            if (remainingMinutes <= availableMinutesToday) {
                current = subtractMinutesWithinWorkDay(current, remainingMinutes, workStart, workEnd, lunchStart, lunchEnd);
                remainingMinutes = 0;
            } else {
                remainingMinutes -= availableMinutesToday;
                current = current.toLocalDate().minusDays(1).atTime(workEnd);
            }
        }

        return current;
    }

    private long calculateAvailableMinutes(LocalTime currentTime, LocalTime workStart, LocalTime workEnd,
                                           LocalTime lunchStart, LocalTime lunchEnd) {
        long totalMinutes = 0;
        
        if (currentTime.isBefore(lunchStart)) {
            long morningMinutes = java.time.Duration.between(currentTime, lunchStart).toMinutes();
            totalMinutes += morningMinutes;
            long afternoonMinutes = java.time.Duration.between(lunchEnd, workEnd).toMinutes();
            totalMinutes += afternoonMinutes;
        } else if (currentTime.isBefore(lunchEnd)) {
            long afternoonMinutes = java.time.Duration.between(lunchEnd, workEnd).toMinutes();
            totalMinutes += afternoonMinutes;
        } else {
            long afternoonMinutes = java.time.Duration.between(currentTime, workEnd).toMinutes();
            totalMinutes += Math.max(0, afternoonMinutes);
        }
        
        return totalMinutes;
    }

    private long calculateAvailableMinutesBackward(LocalTime currentTime, LocalTime workStart, LocalTime workEnd,
                                                    LocalTime lunchStart, LocalTime lunchEnd) {
        long totalMinutes = 0;
        
        if (currentTime.isAfter(lunchEnd)) {
            long afternoonMinutes = java.time.Duration.between(lunchEnd, currentTime).toMinutes();
            totalMinutes += afternoonMinutes;
            long morningMinutes = java.time.Duration.between(workStart, lunchStart).toMinutes();
            totalMinutes += morningMinutes;
        } else if (currentTime.isAfter(lunchStart)) {
            long morningMinutes = java.time.Duration.between(workStart, lunchStart).toMinutes();
            totalMinutes += morningMinutes;
        } else {
            long morningMinutes = java.time.Duration.between(workStart, currentTime).toMinutes();
            totalMinutes += Math.max(0, morningMinutes);
        }
        
        return totalMinutes;
    }

    private LocalDateTime addMinutesWithinWorkDay(LocalDateTime current, long minutesToAdd,
                                                  LocalTime workStart, LocalTime workEnd,
                                                  LocalTime lunchStart, LocalTime lunchEnd) {
        LocalTime currentTime = current.toLocalTime();
        LocalDate currentDate = current.toLocalDate();
        long remainingMinutes = minutesToAdd;
        
        while (remainingMinutes > 0) {
            if (currentTime.isBefore(lunchStart)) {
                long minutesToLunch = java.time.Duration.between(currentTime, lunchStart).toMinutes();
                if (remainingMinutes <= minutesToLunch) {
                    currentTime = currentTime.plusMinutes(remainingMinutes);
                    remainingMinutes = 0;
                } else {
                    remainingMinutes -= minutesToLunch;
                    currentTime = lunchEnd;
                }
            } else if (currentTime.isBefore(lunchEnd)) {
                currentTime = lunchEnd;
            } else {
                long minutesToEnd = java.time.Duration.between(currentTime, workEnd).toMinutes();
                if (remainingMinutes <= minutesToEnd) {
                    currentTime = currentTime.plusMinutes(remainingMinutes);
                    remainingMinutes = 0;
                } else {
                    remainingMinutes -= minutesToEnd;
                    return currentDate.plusDays(1).atTime(workStart).plusMinutes(remainingMinutes);
                }
            }
        }
        
        return currentDate.atTime(currentTime);
    }

    private LocalDateTime subtractMinutesWithinWorkDay(LocalDateTime current, long minutesToSubtract,
                                                         LocalTime workStart, LocalTime workEnd,
                                                         LocalTime lunchStart, LocalTime lunchEnd) {
        LocalTime currentTime = current.toLocalTime();
        LocalDate currentDate = current.toLocalDate();
        long remainingMinutes = minutesToSubtract;
        
        while (remainingMinutes > 0) {
            if (currentTime.isAfter(lunchEnd)) {
                long minutesFromLunchEnd = java.time.Duration.between(lunchEnd, currentTime).toMinutes();
                if (remainingMinutes <= minutesFromLunchEnd) {
                    currentTime = currentTime.minusMinutes(remainingMinutes);
                    remainingMinutes = 0;
                } else {
                    remainingMinutes -= minutesFromLunchEnd;
                    currentTime = lunchStart;
                }
            } else if (currentTime.isAfter(lunchStart)) {
                currentTime = lunchStart;
            } else {
                long minutesFromWorkStart = java.time.Duration.between(workStart, currentTime).toMinutes();
                if (remainingMinutes <= minutesFromWorkStart) {
                    currentTime = currentTime.minusMinutes(remainingMinutes);
                    remainingMinutes = 0;
                } else {
                    remainingMinutes -= minutesFromWorkStart;
                    return currentDate.minusDays(1).atTime(workEnd).minusMinutes(remainingMinutes);
                }
            }
        }
        
        return currentDate.atTime(currentTime);
    }

    public long calculateWorkHoursBetween(LocalDateTime start, LocalDateTime end) {
        return calculateWorkMinutesBetween(start, end) / 60;
    }

    public long calculateWorkMinutesBetween(LocalDateTime start, LocalDateTime end) {
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

        return totalWorkMinutes;
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
        return adjustToWorkDay(dateTime, false);
    }

    public LocalDateTime adjustToWorkDay(LocalDateTime dateTime, boolean backward) {
        WorkCalendar calendar = getWorkCalendar(dateTime.toLocalDate());
        
        if (!calendar.getIsWorkDay()) {
            LocalDate targetDate = dateTime.toLocalDate();
            do {
                if (backward) {
                    targetDate = targetDate.minusDays(1);
                } else {
                    targetDate = targetDate.plusDays(1);
                }
            } while (!isWorkDay(targetDate));
            
            WorkCalendar targetCalendar = getWorkCalendar(targetDate);
            if (backward) {
                return targetDate.atTime(targetCalendar.getWorkEndTime());
            } else {
                return targetDate.atTime(targetCalendar.getWorkStartTime());
            }
        }

        LocalTime time = dateTime.toLocalTime();
        if (time.isBefore(calendar.getWorkStartTime())) {
            return dateTime.toLocalDate().atTime(calendar.getWorkStartTime());
        }
        if (time.isAfter(calendar.getWorkEndTime())) {
            if (backward) {
                return dateTime.toLocalDate().atTime(calendar.getWorkEndTime());
            } else {
                return dateTime.toLocalDate().plusDays(1).atTime(calendar.getWorkStartTime());
            }
        }
        
        return dateTime;
    }

    public int countWorkDaysBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return 0;
        }
        
        int count = 0;
        LocalDate current = start;
        
        while (!current.isAfter(end)) {
            if (isWorkDay(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        
        return count;
    }

    public int countNaturalDaysBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return 0;
        }
        return (int) java.time.Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() + 1;
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

    public List<WorkCalendar> getWorkDaysBetween(LocalDate start, LocalDate end) {
        return workCalendarRepository.findByDateBetweenAndIsWorkDay(start, end, true);
    }
}
