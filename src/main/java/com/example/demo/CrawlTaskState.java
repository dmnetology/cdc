package com.example.demo;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Состояние одного задания краулинга.
 * visited защищает от повторного скачивания одних и тех же страниц.
 */
public class CrawlTaskState {

    /** Номер задания (сквозной для всех запусков сервиса). */
    public final long id;

    /** Откуда пришло задание, автозапуск или запрос по HTTP. */
    public final String label;

    /** Стартовые URL, задающие список разрешённых сайтов. */
    public final List<String> startUrls;

    /** Предельное количество страниц на задание. */
    public final int maxPages;

    /** Глубина обхода ссылок. */
    public final int maxDepth;

    /** Хосты, в пределах которых разрешён обход ссылок. */
    public final Set<String> allowedHosts = ConcurrentHashMap.newKeySet();

    /** Уже поставленные в работу страницы (включая успешно скачанные и с ошибкой). */
    public final Set<String> visited = ConcurrentHashMap.newKeySet();

    /** Страниц, отданных в работу по лимиту. */
    public final AtomicInteger taken = new AtomicInteger();

    /** Задач, находящихся в пуле прямо сейчас. */
    public final AtomicInteger inFlight = new AtomicInteger();

    /** Успешно скачанных страниц. */
    public final AtomicInteger pagesOk = new AtomicInteger();

    /** Страниц, скачанных с ошибкой (сеть, таймаут, код ответа). */
    public final AtomicInteger pagesFailed = new AtomicInteger();

    /** Новых контактов, занесённых в базу этим заданием. */
    public final AtomicInteger contactsSaved = new AtomicInteger();

    /** Статус задания (RUNNING или DONE, после завершения не меняется). */
    public volatile String status = "RUNNING";

    /** Момент запуска. */
    public final long startedAt = System.currentTimeMillis();

    /** Момент завершения, 0 пока задание работает. */
    public volatile long finishedAt = 0;

    public CrawlTaskState(long id, String label, List<String> startUrls, int maxPages, int maxDepth) {
        this.id = id;
        this.label = label;
        this.startUrls = startUrls;
        this.maxPages = maxPages;
        this.maxDepth = maxDepth;
        for (String url : startUrls) {
            try {
                allowedHosts.add(new URI(url).getHost());
            } catch (Exception ignored) {
                // битые стартовые URL отбрасываются при проверке схемы
            }
        }
    }

    /** После завершения время фиксируется. */
    public long elapsedMs() {
        return (finishedAt == 0 ? System.currentTimeMillis() : finishedAt) - startedAt;
    }
}
