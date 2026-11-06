package com.example.demo;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Извлечение из HTML-кода страницы ссылок и контактных данных организаций.
 * Работает по регулярным выражениям, внешних библиотек парсинга не требует.
 */
public final class HtmlPageExtractor {

    /** Ссылка в виде href="...". */
    private static final Pattern HREF = Pattern.compile(
            "href\\s*=\\s*[\"']([^\"'\\s]+)[\"']", Pattern.CASE_INSENSITIVE);

    /** Ссылка на телефон в виде href="tel:...". */
    private static final Pattern TEL_LINK = Pattern.compile(
            "href\\s*=\\s*\"tel:([^\"']+)", Pattern.CASE_INSENSITIVE);

    /** Ссылка на email в виде href="mailto:...". */
    private static final Pattern MAILTO_LINK = Pattern.compile(
            "href\\s*=\\s*\"mailto:([^\"'?]+)", Pattern.CASE_INSENSITIVE);

    /** Телефон в тексте страницы, форматы +7..., 8..., с любыми разделителями. */
    private static final Pattern PHONE_TEXT = Pattern.compile(
            "(?:\\+?7|8)[\\s\\-()]*\\d{3}[\\s\\-()]*\\d{3}[\\s\\-()]*\\d{2}[\\s\\-()]*\\d{2}");

    /** Email в тексте страницы. */
    private static final Pattern EMAIL_TEXT = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * Почтовый адрес по маске «улица ... дом N» (ул., проспект, шоссе и др.).
     * Часть до указания дома ограничена шестидесятью символами, чтобы маска не захватила полстраницы.
     */
    private static final Pattern ADDRESS_TEXT = Pattern.compile(
            "(?:ул|улица|просп|проспект|пр-т|пер|переулок|шоссе|ш|бул|бульвар|наб|набережная)\\.?\\s*"
                    + "[«\"]?[А-ЯЁA-Z0-9][^<>;()\\n]{2,60}?(?:дом|д)\\.?\\s*\\d{1,4}[А-Яа-я]?",
            Pattern.CASE_INSENSITIVE);

    private HtmlPageExtractor() {
    }

    /**
     * Один найденный контакт с типом и исходным значением.
     */
    public record ContactCandidate(ContactType type, String value) {
    }

    /** Расширения файлов, которые краулеру не нужны, ходить по картинкам и стилям запрещено. */
    private static final Set<String> SKIPPED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "svg", "ico", "webp", "css", "js", "mjs",
            "woff", "woff2", "ttf", "eot", "otf", "pdf", "zip", "rar", "mp4", "mp3", "json", "xml");

    /**
     * Все ссылки страницы, приведённые к абсолютному виду.
     * Отброшены mailto, tel, javascript, якоря, статические файлы и битые относительные адреса.
     */
    public static Set<String> extractLinks(String pageUrl, String html) {
        Set<String> links = new LinkedHashSet<>();
        String clean = html.replace("&amp;", "&");
        URI base = URI.create(cleanUrl(pageUrl));
        Matcher matcher = HREF.matcher(clean);
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            if (href.startsWith("mailto:") || href.startsWith("tel:")
                    || href.startsWith("javascript:") || href.startsWith("data:")) {
                continue;
            }
            URI resolved = base.resolve(href);
            if (resolved == null) {
                continue;
            }
            String scheme = resolved.getScheme() == null ? "" : resolved.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                continue;
            }
            String path = resolved.getPath() == null ? "" : resolved.getPath();
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && SKIPPED_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase())) {
                continue;
            }
            // якорь внутри страницы для обхода не нужен
            String result = removeAnchor(resolved.toString());
            if (!result.isBlank()) {
                links.add(result);
            }
        }
        return links;
    }

    /**
     * Все контактные данные страницы, уникальные на уровне одной страницы.
     * Сначала ищутся телефоны и адреса в видимом тексте, их запись с разделителями читается
     * удобнее, чем вариант из tel ссылки, и дубль по номеру всё равно отсекается.
     */
    public static List<ContactCandidate> extractContacts(String html) {
        List<ContactCandidate> found = new ArrayList<>();
        Set<String> perPage = new LinkedHashSet<>();

        // текст без тегов нужен для телефонов и адресов, в HTML-коде они среди разметки
        String text = stripTags(html);
        Matcher matcher = PHONE_TEXT.matcher(text);
        while (matcher.find()) {
            addCandidate(found, perPage, ContactType.PHONE, matcher.group().trim());
        }
        matcher = ADDRESS_TEXT.matcher(text);
        while (matcher.find()) {
            addCandidate(found, perPage, ContactType.ADDRESS, matcher.group().trim());
        }

        matcher = TEL_LINK.matcher(html);
        while (matcher.find()) {
            addCandidate(found, perPage, ContactType.PHONE, matcher.group(1).trim());
        }
        matcher = MAILTO_LINK.matcher(html);
        while (matcher.find()) {
            addCandidate(found, perPage, ContactType.EMAIL, matcher.group(1).trim());
        }

        matcher = EMAIL_TEXT.matcher(text);
        while (matcher.find()) {
            addCandidate(found, perPage, ContactType.EMAIL, matcher.group().trim());
        }
        return found;
    }

    /** Добавление кандидата, если такое значение уже встретилось на текущей странице. */
    private static void addCandidate(List<ContactCandidate> found, Set<String> perPage,
                                     ContactType type, String value) {
        if (value.length() < 4 || value.length() > 300) {
            return;
        }
        String key = type == ContactType.PHONE ? phoneKey(value) : type.name() + "|" + normalize(value);
        if (perPage.add(key)) {
            found.add(new ContactCandidate(type, value));
        }
    }

    /** Вариант значения для сопоставления дублей (регистр не важен, пробелы сведены к одному). */
    public static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    /** Сравнимый между собой вариант телефона, оставлены только цифры, код 8 заменён на 7. */
    public static String phoneKey(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.charAt(0) == '8') {
            digits = "7" + digits.substring(1);
        }
        if (digits.length() == 10) {
            digits = "7" + digits;
        }
        return digits;
    }

    /** Удаление тегов и html-сущностей, чтобы искать контакты по видимому тексту страницы. */
    private static String stripTags(String html) {
        String noTags = html.replaceAll("<script[\\s\\S]*?</script>", " ")
                .replaceAll("<style[\\s\\S]*?</style>", " ")
                .replaceAll("<[^>]+>", " ");
        return noTags.replace("&nbsp;", " ").replace("&amp;", "&");
    }

    /** Отсечение якоря (#...) и замена амперсандов перед разбором ссылки. */
    private static String removeAnchor(String url) {
        int anchor = url.indexOf('#');
        return anchor < 0 ? url : url.substring(0, anchor);
    }

    /** Экранирование пробелов в адресе, иначе URI.create отбрасывает ссылку целиком. */
    private static String cleanUrl(String url) {
        return url.replace(" ", "%20");
    }
}
