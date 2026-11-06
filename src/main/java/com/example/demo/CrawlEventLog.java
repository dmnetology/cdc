package com.example.demo;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Записывающий демон краулера.
 * Задачи складывают события в блокирующую очередь, а отдельный демон-поток
 * (Runnable на new Thread с setDaemon(true)) забирает их через take() и дописывает
 * в файл logs/crawler-events.log. Файл пишет только демон, поэтому отдельная
 * синхронизация записи не нужна.
 */
@Component
public class CrawlEventLog {

    /** Файл журнала, дописывается построчно всё время работы приложения. */
    private static final Path LOG_FILE = Paths.get("logs", "crawler-events.log");

    /** Метка времени в начале каждой строки журнала. */
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Блокирующая очередь производитель-потребитель, задачи и демон работают с ней одновременно. */
    private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();

    /** Поток-демон, вынимающий события из очереди в файл. */
    private Thread writer;

    /** Пометить событие, вызывается из любых потоков краулера. */
    public void publish(String line) {
        events.offer(STAMP.format(LocalDateTime.now()) + " " + line);
    }

    /** Запуск демона, поток один и демонический, приложение от него не зависит при завершении. */
    @PostConstruct
    public void start() {
        writer = new Thread(this::drain, "log-daemon");
        writer.setDaemon(true);
        writer.start();
    }

    /** Остановка демона при завершении приложения, take() внутри прерывается исключением. */
    @PreDestroy
    public void stop() {
        writer.interrupt();
    }

    /** Потребитель очереди, take() блокирует поток, пока событий нет. */
    private void drain() {
        try {
            while (true) {
                append(events.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Дописывание строки в журнал, при недоступности файла событие не теряется, а печатается в консоль. */
    private void append(String line) {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(LOG_FILE, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("[лог] файл журнала недоступен (" + e.getMessage() + "), событие в консоль " + line);
        }
    }
}
