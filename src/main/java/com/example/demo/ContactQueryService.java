package com.example.demo;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Выдача собранных контактов.
 * Требование задания здесь выполнено напрямую, контакты читаются из H2 и сортируются
 * через parallelStream по параметрам запроса.
 */
@Service
public class ContactQueryService {

    /** Доступные сортировки, имена совпадают со значением параметра sort. */
    private static final Map<String, Comparator<Contact>> SORTERS = Map.of(
            "value", Comparator.comparing(Contact::getValue),
            "type", Comparator.comparing(Contact::getType),
            "source", Comparator.comparing(Contact::getSourceUrl,
                    Comparator.nullsLast(Comparator.naturalOrder())),
            "time", Comparator.comparing(Contact::getFoundAt),
            "id", Comparator.comparing(Contact::getId));

    /** Граница размера страницы, чтобы запрос излишне большой страницы не уронил выдачу. */
    private static final int MAX_PAGE_SIZE = 200;

    private final ContactRepository repository;

    public ContactQueryService(ContactRepository repository) {
        this.repository = repository;
    }

    /**
     * Страница контактов, фильтрация и сортировка выполняются одним parallelStream.
     * Фильтры type и search необязательны, пустые просто отбрасываются,
     * неизвестное имя сортировки и направление подменяются значениями по умолчанию.
     */
    public ContactPage getContacts(int page, int size, String sortBy, String order, String type, String search) {
        List<Contact> all = repository.findAll();
        Comparator<Contact> comparator = SORTERS.getOrDefault(
                sortBy.toLowerCase(Locale.ROOT), SORTERS.get("value"));
        if ("desc".equalsIgnoreCase(order)) {
            comparator = comparator.reversed();
        }
        String wantedType = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);
        String wantedText = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        List<Contact> sorted = all.parallelStream()
                .filter(c -> wantedType == null || wantedType.equals(c.getType()))
                .filter(c -> wantedText == null || c.getValue().toLowerCase(Locale.ROOT).contains(wantedText))
                .sorted(comparator)
                .collect(Collectors.toList());
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int from = Math.max(0, page) * pageSize;
        if (from >= sorted.size()) {
            return new ContactPage(sorted.size(), from / pageSize, pageSize, List.of());
        }
        int to = Math.min(from + pageSize, sorted.size());
        return new ContactPage(sorted.size(), from / pageSize, pageSize, sorted.subList(from, to));
    }

    /**
     * Разрез выданных контактов вместе с параметрами пагинации.
     */
    public record ContactPage(long total, int page, int size, List<Contact> records) {
    }
}
