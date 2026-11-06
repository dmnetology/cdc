package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Встроенный демонстрационный источник заданий.
 * Три страницы организаций, каждая с главной страницей и страницей контактов.
 */
@RestController
public class DemoSiteController {

    /** Одна организация демо-источника. */
    private record Company(String slug, String name, String phone, String email, String address, String about) {
    }

    private static final Company[] COMPANIES = {
            new Company("test1", "ООО «test1»", "+7 (495) 123-45-67", "info@test1.ru",
                    "ул. Профсоюзная, д. 42, г. Москва",
                    "Строительная компания полного цикла, офисы и склад в Москве"),
            new Company("test2", "АО «test2»", "+7 (495) 987-65-43", "support@test2.ru",
                    "г. Москва, ул. Тверская, д. 18, офис 505",
                    "Оператор связи и провайдер телекоммуникационных услуг"),
            new Company("test3", "ООО «test3»", "+7 (812) 555-10-20", "sales@test3.ru",
                    "г. Санкт-Петербург, ш. Пулковское, д. 7",
                    "Грузовые перевозки и складская логистика по стране"),
    };

    /** Главная страница организации. */
    @GetMapping(value = "/demo-sites/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public String home(@PathVariable String slug) {
        Company company = bySlug(slug);
        if (company == null) {
            return "<html><body><h1>Страница не найдена в демо-источнике</h1></body></html>";
        }
        StringBuilder html = basePage(company, company.name());
        html.append("<h2>О компании</h2>\n<p>").append(company.about()).append("</p>\n");
        html.append("<p>Звоните по номеру <a href=\"tel:").append(digitsOnly(company.phone()))
                .append("\">").append(company.phone()).append("</a>.");
        html.append(" Пишите на <a href=\"mailto:").append(company.email()).append("\">")
                .append(company.email()).append("</a>. Наш адрес ").append(company.address()).append(".</p>\n");
        html.append("<p>Наши контакты указаны на странице ");
        html.append("<a href=\"/demo-sites/").append(company.slug()).append("/contacts\">")
                .append(company.name()).append(" — контакты</a>.</p>\n");
        html.append(footer(company));
        return html.toString();
    }

    /** Страница контактов организации. */
    @GetMapping(value = "/demo-sites/{slug}/contacts", produces = MediaType.TEXT_HTML_VALUE)
    public String contacts(@PathVariable String slug) {
        Company company = bySlug(slug);
        if (company == null) {
            return "<html><body><h1>Страница не найдена в демо-источнике</h1></body></html>";
        }
        StringBuilder html = basePage(company, "Контакты " + company.name());
        html.append("<h2>Контактные данные</h2>\n<p>Телефон <a href=\"tel:").append(digitsOnly(company.phone()))
                .append("\">").append(company.phone()).append("</a></p>\n");
        html.append("<p>Email <a href=\"mailto:").append(company.email()).append("\">")
                .append(company.email()).append("</a></p>\n");
        html.append("<p>Почтовый адрес ").append(company.address()).append("</p>\n");
        html.append("<p>Вернуться на <a href=\"/demo-sites/").append(company.slug()).append("\">главную страницу</a>.</p>\n");
        html.append(footer(company));
        return html.toString();
    }

    /** Пути страниц демо-источника, стартовые URL краулера по умолчанию собираются из них. */
    public static String[] pagePaths() {
        String[] paths = new String[COMPANIES.length];
        for (int i = 0; i < COMPANIES.length; i++) {
            paths[i] = "/demo-sites/" + COMPANIES[i].slug();
        }
        return paths;
    }

    /** Общая обвязка страницы с названием и меню. */
    private StringBuilder basePage(Company company, String title) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"utf-8\"><title>").append(title)
                .append("</title></head><body>\n");
        html.append("<h1>").append(title).append("</h1>\n");
        return html;
    }

    /** Ссылки на страницы организаций-партнёров, чтобы краулер обошёл весь демо-источник. */
    private String footer(Company current) {
        StringBuilder html = new StringBuilder("<hr><p>Организации из каталога:</p><ul>\n");
        for (Company company : COMPANIES) {
            html.append("<li><a href=\"/demo-sites/").append(company.slug()).append("\">")
                    .append(company.name()).append("</a></li>\n");
        }
        html.append("</ul>\n</body></html>");
        return html.toString();
    }

    private Company bySlug(String slug) {
        for (Company company : COMPANIES) {
            if (company.slug().equals(slug)) {
                return company;
            }
        }
        return null;
    }

    /** Адрес tel-ссылки строится из цифр телефона. */
    private String digitsOnly(String phone) {
        return "+" + phone.replaceAll("\\D", "");
    }
}
