package com.example.demo;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ядро краулера контактных данных.
 * Задание обходит стартовые сайты на кастомном пуле потоков, каждый рабочий поток скачивает
 * страницу, извлекает контакты и ссылки, новые страницы встают в очередь пула изнутри задач.
 * Задание считается завершенным, когда ни одна страница не находится в работе.
 */
@Service
public class ContactCrawlerService {

    /** Минимальное и максимальное число живых потоков пула. */
    private static final int CORE_POOL_SIZE = 8;
    private static final int MAX_POOL_SIZE = 16;

    /** Ёмкость очереди пула. */
    private static final int QUEUE_CAPACITY = 1_000;

    /** Настройки задания по умолчанию и их верхние границы. */
    private static final int DEFAULT_MAX_PAGES = 15;
    private static final int MAX_PAGES_LIMIT = 100;
    private static final int DEFAULT_MAX_DEPTH = 2;
    private static final int MAX_DEPTH_LIMIT = 3;

    private final AtomicLong taskSequence = new AtomicLong();

    /** Состояния всех заданий, читаются эндпоинтом статуса. */
    private final Map<Long, CrawlTaskState> taskStates = new ConcurrentHashMap<>();

    /** Уже занесённые в базу контакты, ключи вида тип плюс нормализованное значение. */
    private final Set<String> seenContacts = ConcurrentHashMap.newKeySet();

    private final ContactRepository repository;
    private final PageFetcher fetcher;
    private final Environment environment;
    private final ThreadPoolExecutor executor;
    private final CrawlEventLog eventLog;

    public ContactCrawlerService(ContactRepository repository, PageFetcher fetcher, Environment environment,
                                 CrawlEventLog eventLog) {
        this.eventLog = eventLog;
        this.repository = repository;
        this.fetcher = fetcher;
        this.environment = environment;
        // пул на все задания приложения сразу, а не отдельный пул под каждый запуск
        this.executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                30,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                Thread.ofPlatform().name("crawler-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Запуск задания (по HTTP или от планировщика).
     * Пустой список стартовых URL переключает задание на встроенный демо-источник.
     */
    public CrawlTaskState startCrawl(CrawlRequest request, String label) {
        CrawlRequest safe = request == null ? new CrawlRequest() : request;
        List<String> requested = httpUrls(safe.getStartUrls() == null ? List.of() : safe.getStartUrls());
        boolean demoSource = requested.isEmpty();
        List<String> startUrls = demoSource ? demoStartUrls() : requested;
        int maxPages = clamp(safe.getMaxPages() == null ? DEFAULT_MAX_PAGES : safe.getMaxPages(), 1, MAX_PAGES_LIMIT);
        int maxDepth = clamp(safe.getMaxDepth() == null ? DEFAULT_MAX_DEPTH : safe.getMaxDepth(), 0, MAX_DEPTH_LIMIT);

        CrawlTaskState state = new CrawlTaskState(taskSequence.incrementAndGet(), label, startUrls, maxPages, maxDepth);
        taskStates.put(state.id, state);

        log("[краул] задание " + state.id + " запущено (" + label + "): стартовых URL "
                + startUrls.size() + ", лимит страниц " + maxPages + ", глубина обхода " + maxDepth
                + (demoSource ? " (встроенный демо-источник)" : ""));
        for (String url : startUrls) {
            enqueue(url, 0, state);
        }
        return state;
    }

    /** Автоматический демонстрационный краулинг, планировщик вызывает его через 10 секунд после старта. */
    public void startAutoDemo() {
        log("[планировщик] автозапуск демонстрационного краулинга (встроенный источник, сеть не нужна)");
        startCrawl(new CrawlRequest(), "автозапуск по расписанию");
    }

    /** Одна точка вывода, строка попадает и в консоль, и в файловый журнал. */
    private void log(String line) {
        System.out.println(line);
        eventLog.publish(line);
    }

    /** Сводка для периодической строки по расписанию. */
    public String summary() {
        int running = 0;
        int pages = 0;
        for (CrawlTaskState state : taskStates.values()) {
            if ("RUNNING".equals(state.status)) {
                running++;
            }
            pages += state.pagesOk.get() + state.pagesFailed.get();
        }
        return "в базе контактов " + repository.count() + ", заданий " + taskStates.size()
                + " (в работе " + running + "), страниц скачано " + pages;
    }

    /** Состояние задания, null если задания с таким номером нет. */
    public CrawlTaskState findTask(long taskId) {
        return taskStates.get(taskId);
    }

    /** Ставит страницу в работу, когда она на разрешённом сайте, не посещалась. */
    private void enqueue(String url, int depth, CrawlTaskState state) {
        if (depth > state.maxDepth || !hostAllowed(url, state)) {
            return;
        }
        // страница регистрируется в счётчике до отправки в пул, иначе последний декремент
        // мог бы зафиксировать завершение задания при непоставленной странице
        state.inFlight.incrementAndGet();
        if (!reserve(state, url)) {
            state.inFlight.decrementAndGet();
            return;
        }
        executor.execute(() -> process(url, depth, state));
    }

    /** Резерв страницы и лимитов, чтобы страница не скачалась дважды и задание не обошло лимит. */
    private boolean reserve(CrawlTaskState state, String url) {
        synchronized (state) {
            if (!state.visited.add(url)) {
                return false;
            }
            if (state.taken.incrementAndGet() > state.maxPages) {
                state.visited.remove(url);
                state.taken.decrementAndGet();
                return false;
            }
            return true;
        }
    }

    /** Скачивание одной страницы, поиск контактов и ссылок на следующие страницы. */
    private void process(String url, int depth, CrawlTaskState state) {
        try {
            FetchedPage page = fetcher.fetch(url);
            if (!page.ok()) {
                state.pagesFailed.incrementAndGet();
                log("[краул] " + Thread.currentThread().getName() + ": страница " + url
                        + " не скачана, причина " + page.error());
                return;
            }
            state.pagesOk.incrementAndGet();

            List<HtmlPageExtractor.ContactCandidate> candidates = HtmlPageExtractor.extractContacts(page.html());
            int saved = 0;
            int phones = 0;
            int emails = 0;
            int addresses = 0;
            for (HtmlPageExtractor.ContactCandidate candidate : candidates) {
                if (persistIfNew(candidate, url, state)) {
                    saved++;
                }
                switch (candidate.type()) {
                    case PHONE -> phones++;
                    case EMAIL -> emails++;
                    case ADDRESS -> addresses++;
                }
            }
            log("[краул] " + Thread.currentThread().getName() + ": страница " + url
                    + ", телефонов " + phones + ", email " + emails + ", адресов " + addresses
                    + ", новых в базе " + saved);

            if (depth < state.maxDepth) {
                for (String link : HtmlPageExtractor.extractLinks(url, page.html())) {
                    enqueue(link, depth + 1, state);
                }
            }
        } catch (Exception e) {
            state.pagesFailed.incrementAndGet();
            log("[краул] " + Thread.currentThread().getName() + ": страница " + url
                    + " упала с исключением " + e);
        } finally {
            if (state.inFlight.decrementAndGet() == 0) {
                state.finishedAt = System.currentTimeMillis();
                state.status = "DONE";
                log("[краул] задание " + state.id + " завершено за " + state.elapsedMs()
                        + " мс, страниц скачано " + state.pagesOk.get() + ", с ошибкой "
                        + state.pagesFailed.get() + ", новых контактов " + state.contactsSaved.get());
            }
        }
    }

    /**
     * Запись контакта в базу. Дубли отсекаются набором seenContacts, а если два разных задания
     * собрали один контакт, от повторной вставки защищает уникальный индекс в таблице.
     */
    private synchronized boolean persistIfNew(HtmlPageExtractor.ContactCandidate candidate,
                                              String sourceUrl, CrawlTaskState state) {
        String normalized = candidate.type() == ContactType.PHONE
                ? HtmlPageExtractor.phoneKey(candidate.value())
                : HtmlPageExtractor.normalize(candidate.value());
        if (!seenContacts.add(candidate.type().name() + "|" + normalized)) {
            return false;
        }
        Contact contact = new Contact();
        contact.setType(candidate.type().name());
        contact.setValue(candidate.value().trim());
        contact.setSourceUrl(sourceUrl);
        contact.setTaskLabel(state.label);
        contact.setFoundAt(LocalDateTime.now());
        try {
            repository.save(contact);
            state.contactsSaved.incrementAndGet();
            // в файловый журнал добавляется строка на каждый новый контакт, это хранилище вне базы
            eventLog.publish("[контакт] " + contact.getType() + " | " + contact.getValue()
                    + " | " + contact.getSourceUrl());
            return true;
        } catch (Exception e) {
            // контакт уже попал в базу из другого задания
            return false;
        }
    }

    /** Обход разрешён только в пределах сайтов из стартового списка. */
    private boolean hostAllowed(String url, CrawlTaskState state) {
        try {
            return state.allowedHosts.contains(new URI(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    /** Отбор корректных http и https адресов из полученного списка. */
    private List<String> httpUrls(List<String> requested) {
        List<String> result = new ArrayList<>();
        for (String url : requested) {
            if (url == null || url.isBlank()) {
                continue;
            }
            try {
                String scheme = new URI(url.trim()).getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    result.add(url.trim());
                }
            } catch (Exception ignored) {
                // адрес не похож на URL
            }
        }
        return result;
    }

    /** Стартовые страницы встроенного источника, слаги организаций лежат в DemoSiteController. */
    private List<String> demoStartUrls() {
        String base = baseUrl();
        List<String> urls = new ArrayList<>();
        for (String path : DemoSiteController.pagePaths()) {
            urls.add(base + path);
        }
        return urls;
    }

    /** Адрес самого приложения, чтобы попасть во встроенные страницы. */
    private String baseUrl() {
        String port = environment.getProperty("local.server.port");
        return "http://localhost:" + (port != null ? port : "8080");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Остановка пула при завершении приложения. */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
