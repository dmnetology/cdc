package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты выдачи контактов, репозиторий заменяется заглушкой,
 * проверяются фильтрация, сортировка и пагинация на фиксированной выборке.
 */
@ExtendWith(MockitoExtension.class)
class ContactQueryServiceTest {

    @Mock
    private ContactRepository repository;

    private ContactQueryService service;

    @BeforeEach
    void setUp() {
        service = new ContactQueryService(repository);
    }

    /** Фиксированная выборка, как будто она вернулась из базы. */
    private List<Contact> sample() {
        return List.of(
                contact(ContactType.EMAIL, "info@test.ru"),
                contact(ContactType.ADDRESS, "ул. Профсоюзная, д. 42"),
                contact(ContactType.PHONE, "+7 (495) 123-45-67"),
                contact(ContactType.PHONE, "+7 (812) 555-10-20"));
    }

    private Contact contact(ContactType type, String value) {
        Contact contact = new Contact();
        contact.setType(type.name());
        contact.setValue(value);
        contact.setSourceUrl("http://test.ru/" + value);
        contact.setFoundAt(LocalDateTime.of(2026, 11, 5, 12, 0));
        return contact;
    }

    @Test
    void filtersByType() {
        when(repository.findAll()).thenReturn(sample());
        ContactQueryService.ContactPage page = service.getContacts(0, 50, "value", "asc", "PHONE", null);

        assertEquals(2, page.total());
        assertTrue(page.records().stream().allMatch(c -> "PHONE".equals(c.getType())));
    }

    @Test
    void filtersBySearchSubstringCaseInsensitive() {
        when(repository.findAll()).thenReturn(sample());
        ContactQueryService.ContactPage page = service.getContacts(0, 50, "value", "asc", null, "профсоюзная");

        assertEquals(1, page.total());
        assertEquals("ул. Профсоюзная, д. 42", page.records().get(0).getValue());
    }

    @Test
    void appliesTypeAndSearchFiltersTogether() {
        when(repository.findAll()).thenReturn(sample());
        ContactQueryService.ContactPage page = service.getContacts(0, 50, "value", "asc", "PHONE", "495");

        assertEquals(1, page.total());
        assertEquals("+7 (495) 123-45-67", page.records().get(0).getValue());
    }

    @Test
    void sortsByValueAscending() {
        when(repository.findAll()).thenReturn(sample());
        ContactQueryService.ContactPage page = service.getContacts(0, 50, "value", "asc", null, null);

        assertEquals("+7 (495) 123-45-67", page.records().get(0).getValue());
        assertEquals(4, page.records().size());
    }

    @Test
    void paginatesWithSecondPage() {
        when(repository.findAll()).thenReturn(sample());
        ContactQueryService.ContactPage page = service.getContacts(1, 2, "value", "asc", null, null);

        assertEquals(4, page.total());
        assertEquals(1, page.page());
        assertEquals(2, page.records().size());
    }

    @Test
    void returnsEmptyPageBeyondRange() {
        when(repository.findAll()).thenReturn(sample());
        ContactQueryService.ContactPage page = service.getContacts(9, 50, "value", "asc", null, null);

        assertEquals(4, page.total());
        assertTrue(page.records().isEmpty());
    }
}
