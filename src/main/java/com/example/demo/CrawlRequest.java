package com.example.demo;

import java.util.List;

/**
 * Тело POST-запроса на запуск краулинга.
 * Список стартовых URL может быть пустым, тогда берётся встроенный демонстрационный источник.
 */
public class CrawlRequest {

    /** Стартовые URL сайтов компаний или каталогов. */
    private List<String> startUrls;

    /** Предельное число страниц на одно задание. */
    private Integer maxPages;

    /** Насколько уровней по ссылкам от стартовой страницы можно уходить. */
    private Integer maxDepth;

    public List<String> getStartUrls() {
        return startUrls;
    }

    public void setStartUrls(List<String> startUrls) {
        this.startUrls = startUrls;
    }

    public Integer getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(Integer maxPages) {
        this.maxPages = maxPages;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
    }
}
