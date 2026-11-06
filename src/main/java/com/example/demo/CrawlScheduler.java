package com.example.demo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Запуск краулинга по расписанию.
 * ScheduledExecutorService один раз, через десять секунд после старта, запускает демонстрационное
 * задание на встроенном источнике, а периодическая строка по @Scheduled показывает сводку сервиса.
 */
@Component
public class CrawlScheduler {

    private static final int AUTO_DEMO_DELAY_S = 10;

    private final ContactCrawlerService crawlerService;
    private final CrawlEventLog eventLog;

    /** Планировщик с именованными потоками, чтобы в выводе было видно, кто запускает задание. */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("sched-", 0).daemon(true).factory());

    public CrawlScheduler(ContactCrawlerService crawlerService, CrawlEventLog eventLog) {
        this.crawlerService = crawlerService;
        this.eventLog = eventLog;
        scheduler.schedule(crawlerService::startAutoDemo, AUTO_DEMO_DELAY_S, TimeUnit.SECONDS);
    }

    /** Сводка по расписанию, по ней видно, что сервис работает и наполняет базу. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void printSummary() {
        String line = "[по расписанию] " + crawlerService.summary();
        System.out.println(line);
        eventLog.publish(line);
    }

    /** Остановка планировщика при завершении приложения. */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
