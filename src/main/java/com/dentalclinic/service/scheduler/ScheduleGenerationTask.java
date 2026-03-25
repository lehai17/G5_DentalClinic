package com.dentalclinic.service.scheduler;

import com.dentalclinic.service.admin.AdminSlotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduleGenerationTask {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleGenerationTask.class);

    @Autowired
    private AdminSlotService adminSlotService;

    /**
     * Tự động sinh lịch (slotrender) cho 30 ngày tiếp theo.
     * Chạy mỗi ngày một lần vào lúc 00:01 AM.
     */
    @Scheduled(cron = "0 1 0 * * *")
    public void autoGenerateRollingSchedule() {
        logger.info("Bắt đầu tự động sinh lịch làm việc cho 30 ngày tới (slotrender)...");
        try {
            adminSlotService.ensureRollingSchedule(30);
            logger.info("Đã hoàn thành sinh lịch tự động.");
        } catch (Exception e) {
            logger.error("Lỗi khi tự động sinh lịch: {}", e.getMessage());
        }
    }
}
