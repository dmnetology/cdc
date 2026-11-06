package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-интерфейс краулера.
 * POST /crawl запускает задание, GET /status показывает статистику задания, GET /answer выдаёт собранные контакты.
 * Ответы объявляют кодировку UTF-8 явно.
 */
@RestController
public class CrawlController {

    /** Размер страницы выдачи по умолчанию. */
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ContactCrawlerService crawlerService;
    private final ContactQueryService queryService;

    public CrawlController(ContactCrawlerService crawlerService, ContactQueryService queryService) {
        this.crawlerService = crawlerService;
        this.queryService = queryService;
    }

    /**
     * Запуск задания краулинга. Тело не обязательно, пустой список стартовых URL
     * включает встроенный демонстрационный источник.
     */
    @PostMapping(value = "/crawl", produces = "application/json;charset=UTF-8")
    public Map<String, Object> start(@RequestBody(required = false) CrawlRequest request) {
        CrawlTaskState state = crawlerService.startCrawl(request, "запуск по HTTP");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", state.id);
        response.put("status", state.status);
        response.put("startUrls", state.startUrls);
        response.put("maxPages", state.maxPages);
        response.put("maxDepth", state.maxDepth);
        response.put("message", "обход запущен, результаты доступны через GET /answer, детали задания через GET /status/"
                + state.id);
        return response;
    }

    /** Список собранных контактов с пагинацией, сортировкой и фильтрацией через parallelStream. */
    @GetMapping(value = "/answer", produces = "application/json;charset=UTF-8")
    public ContactQueryService.ContactPage answer(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "value") String sort,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        return queryService.getContacts(page, size, sort, order, type, search);
    }

    /** Статистика одного задания краулинга. */
    @GetMapping(value = "/status/{taskId}", produces = "application/json;charset=UTF-8")
    public Map<String, Object> status(@PathVariable long taskId) {
        CrawlTaskState state = crawlerService.findTask(taskId);
        if (state == null) {
            return Map.of("error", "задание " + taskId + " не найдено");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", state.id);
        result.put("label", state.label);
        result.put("status", state.status);
        result.put("startUrls", state.startUrls);
        result.put("maxPages", state.maxPages);
        result.put("maxDepth", state.maxDepth);
        result.put("pagesDownloaded", state.pagesOk.get());
        result.put("pagesFailed", state.pagesFailed.get());
        result.put("newContacts", state.contactsSaved.get());
        result.put("elapsedMs", state.elapsedMs());
        return result;
    }
}
