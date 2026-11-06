package com.example.demo;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Скачивание страниц через RestTemplate.
 * Задание требует межсервисного взаимодействия, здесь выбран RestTemplate.
 */
@Component
public class PageFetcher {

    /** Ждём ответа от сайта не больше 3 секунд на соединение и 5 - на чтение. */
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int MAX_BODY_BYTES = 2_000_000;

    /** Представление краулера сайтов, без него часть площадок отвечает отказом. */
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; UniversityContactsCrawler/1.0)";

    /** Кодировка страницы, не указанная в заголовках, ищется в meta теге. */
    private static final Pattern META_CHARSET =
            Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;

    public PageFetcher(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** Скачивает страницу и перекодирует её в текст по кодировке из заголовков или meta тега. */
    public FetchedPage fetch(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.ALL));
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] full = response.getBody();
            if (full == null || full.length == 0) {
                return FetchedPage.failure("пустой ответ");
            }
            byte[] body = full.length > MAX_BODY_BYTES ? java.util.Arrays.copyOf(full, MAX_BODY_BYTES) : full;
            String html = new String(body, resolveCharset(response, body));
            return FetchedPage.success(html);
        } catch (Exception e) {
            // ошибки сети, таймауты и коды ответов считаются нерезультативным визитом, задание продолжается
            return FetchedPage.failure(e.getMessage() == null ? String.valueOf(e) : e.getMessage());
        }
    }

    /** Кодировка из Content-Type, при отсутствии поиски в теге meta, в крайнем случае UTF-8. */
    private Charset resolveCharset(ResponseEntity<byte[]> response, byte[] body) {
        try {
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null && contentType.getParameter("charset") != null) {
                return Charset.forName(contentType.getParameter("charset"));
            }
            String head = new String(body, 0, Math.min(body.length, 4_000), StandardCharsets.US_ASCII);
            Matcher matcher = META_CHARSET.matcher(head);
            if (matcher.find()) {
                return Charset.forName(matcher.group(1));
            }
        } catch (Exception ignored) {
            // ниже используется кодировка по умолчанию
        }
        return StandardCharsets.UTF_8;
    }
}
