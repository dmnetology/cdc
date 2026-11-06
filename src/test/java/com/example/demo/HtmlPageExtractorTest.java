package com.example.demo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Юнит-тесты извлечения ссылок и контактов из HTML.
 * Работают без Spring, проверяют чистые функции парсера.
 */
class HtmlPageExtractorTest {

    /** Мини-страница, где есть всё и с дублями для проверки дедупликации. */
    private static final String PAGE = "<html><body>"
            + "<h2>О компании</h2>"
            + "<a href=\"/contacts\">Контакты</a>"
            + "<a href=\"https://cbr.ru/rules?#anchor\">Ссылка с якорем</a>"
            + "<a href=\"/images/logo.png\">Логотип</a>"
            + "<a href=\"mailto:info@test.ru\">info@test.ru</a>"
            + "<a href=\"tel:+74951234567\">+7 (495) 123-45-67</a>"
            + "<p>Наш адрес ул. Профсоюзная, д. 42, электронная почта info@test.ru,"
            + " запасной телефон 8 800 300-30-00</p>"
            + "</body></html>";

    @Test
    void findsPhoneEmailAndAddress() {
        List<HtmlPageExtractor.ContactCandidate> contacts = HtmlPageExtractor.extractContacts(PAGE);

        long phones = contacts.stream().filter(c -> c.type() == ContactType.PHONE).count();
        long emails = contacts.stream().filter(c -> c.type() == ContactType.EMAIL).count();
        long addresses = contacts.stream().filter(c -> c.type() == ContactType.ADDRESS).count();

        assertEquals(2, phones, "телефон из текста и запасной по тексту");
        assertEquals(1, emails, "mailto и текстовая почта дают один контакт");
        assertEquals(1, addresses, "маска улица плюс дом");

        // в базе остаётся читаемый вариант телефона из текста, а не из tel ссылки
        String phoneValue = contacts.stream()
                .filter(c -> c.type() == ContactType.PHONE)
                .map(HtmlPageExtractor.ContactCandidate::value)
                .filter(v -> v.contains("123-45-67"))
                .findFirst().orElseThrow();
        assertEquals("+7 (495) 123-45-67", phoneValue);
    }

    @Test
    void deduplicatePhonesByDigits() {
        assertEquals(
                HtmlPageExtractor.phoneKey("+7 (495) 123-45-67"),
                HtmlPageExtractor.phoneKey("+74951234567"),
                "одинаковый телефон с разделителями и без даёт один ключ");
        assertEquals("78003003000", HtmlPageExtractor.phoneKey("8 800 300-30-00"));
    }

    @Test
    void buildsAbsoluteLinksAndSkipsNoise() {
        Set<String> links = HtmlPageExtractor.extractLinks("http://localhost:8080/", PAGE);

        assertTrue(links.contains("http://localhost:8080/contacts"), "относительные ссылки разкрываются");
        assertTrue(links.contains("https://cbr.ru/rules?"), "абсолютная ссылка, якорь снят");
        assertFalse(links.stream().anyMatch(l -> l.endsWith(".png")), "картинки не обходятся");
        assertFalse(links.stream().anyMatch(l -> l.startsWith("mailto:")), "почта не ссылка для обхода");
    }
}
