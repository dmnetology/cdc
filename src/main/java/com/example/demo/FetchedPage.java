package com.example.demo;

/**
 * Результат скачивания страницы.
 * В удачном случае заполнен html, в неудачном поле error описывает причину.
 */
public record FetchedPage(boolean ok, String html, String error) {

    /** Страница скачана. */
    public static FetchedPage success(String html) {
        return new FetchedPage(true, html, null);
    }

    /** Страница не скачалась. */
    public static FetchedPage failure(String error) {
        return new FetchedPage(false, null, error);
    }
}
