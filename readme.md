# Итоговое задание. Многопоточный веб-краулер контактных данных организаций

Сервис на Spring Boot по заданным стартовым URL обходит сайты, извлекает из страниц ссылки и открытые контактные данные организаций (телефоны, email, почтовые адреса), заносит их в встроенную базу H2 и отдаёт через GET /answer с пагинацией и сортировкой.

Требуется JDK 21. Базу и библиотеки подтянет Maven, никакие дополнительные настройки не нужны. Запускать можно как с доступом в интернет, так и без него (приложение содержит собственные придуманные страницы-источники для демонстрации работы).

## Последовательность действий

1. **Запуск.** Открыть проект в IntelliJ IDEA Community (JDK 21) и нажать пуск у класса `src/main/java/com/example/demo/DemoApplication`. Сборка идёт Maven-обёрткой, зависимости скачаются при первом запуске.
   Вариант из консоли без IDEA (из корня проекта выполнить): `.\mvnw.cmd spring-boot:run` (Windows) или `./mvnw spring-boot:run` (Linux/Mac).
2. **Автоматическая демонстрация.** Примерно через 10 секунд после старта приложение само запустит демонстрационный обход встроенного источника (в консоли появится строка `[планировщик] автозапуск демонстрационного краулинга`). Встроенный источник — это три страницы организаций, которые раздаёт сам сервис (`/demo-sites/test1`, `/demo-sites/test2`, `/demo-sites/test3`), он работает без сети и служит для демонстрации.
3. **Запуск задания вручную.** POST на `http://localhost:8080/crawl` с телом:
   ```json
   {"startUrls": [], "maxPages": 15, "maxDepth": 2}
   ```
   Пустой список стартовых URL выбирает встроенный демо-источник. Пример через curl:
   ```text
   curl -s -X POST -H "Content-Type: application/json" -d "{\"startUrls\": []}" http://localhost:8080/crawl
   ```
   Ответ:
   ```text
   {"taskId":2,"status":"RUNNING","startUrls":["http://localhost:8080/demo-sites/test1", ...],"message":"обход запущен, ..."}
   ```
4. **Результаты собранных контактов.** GET на `http://localhost:8080/answer?page=0&size=50&sort=value&order=asc` (все параметры опциональны). Фильтрация и сортировка выполняются через parallelStream: `sort` (допустимо `value`, `type`, `source`, `time`, `id`), направление `order` (`asc`/`desc`), фильтр `type` (`PHONE`/`EMAIL`/`ADDRESS`) и поиск по подстроке значения `search`.
   ```text
   curl -s "http://localhost:8080/answer?sort=type&order=desc"
   curl -s "http://localhost:8080/answer?type=PHONE&search=495"
   ```
   Ответ (фрагмент):
   ```text
   {"total":9,"page":0,"size":50,"records":[
     {"id":5,"type":"PHONE","value":"+7 (495) 123-45-67","sourceUrl":"http://localhost:8080/demo-sites/test1",...},
     {...,"type":"EMAIL","value":"info@test1.ru",...},
     {...,"type":"ADDRESS","value":"ул. Профсоюзная, д. 42",...}]}
   ```
Вариант запуска через PS:
   ```text
   Invoke-RestMethod -Method Post -ContentType "application/json" -Body '{"startUrls": ["https://www.rbc.ru"], "maxPages": 10, "maxDepth": 1}' http://localhost:8080/crawl
   ```

5. **Статистика задания.** GET на `http://localhost:8080/status/1` (номер задания из ответа шага 3):
   ```text
   {"taskId":1,"status":"DONE","pagesDownloaded":6,"pagesFailed":0,"newContacts":9,"elapsedMs":402}
   ```
6. **Задание по реальным сайтам (при наличии интернета).** Те же шаги с телом, например:
   ```json
   {"startUrls": ["https://www.cbr.ru", "https://netology.ru"], "maxPages": 6, "maxDepth": 1}
   ```
7. **База данных.** На `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:contactsdb`, пользователь `sa`, пароля нет) видно содержимое таблицы `CONTACTS`.

## Эндпоинты

| Запрос | Назначение |
|---|---|
| POST /crawl | запуск задания краулинга |
| GET /answer?page=&size=&sort=&order=&type=&search= | собранные контакты, фильтрация и сортировка через parallelStream |
| GET /status/{taskId} | статистика одного задания |
| GET /demo-sites/{slug} | страницы встроенного демонстрационного источника |

## Что демонстрирует проект
- кастомный ThreadPoolExecutor (ядро 8, максимум 16, именованные потоки crawler-N, очередь 1000, CallerRunsPolicy);
- демон-поток логирования, краулер кладёт события в блокирующую очередь LinkedBlockingQueue, поток log-daemon (Runnable на new Thread с setDaemon) вынимает их и дописывает в файл журнала logs/crawler-events.log, там же регистрируется каждый занесённый контакт;
- обход, у которого нет центрального диспетчера, рабочие потоки сами ставят найденные страницы в очередь пула, завершение фиксируется по счётчику страниц в работе;
- потокобезопасные структуры - ConcurrentHashMap.newKeySet (посещённые страницы, занесённые контакты), ConcurrentHashMap (состояния заданий), AtomicInteger (счётчики), synchronized-резерв страницы и записи контакта;
- запуск по расписанию - ScheduledExecutorService (демонстрационное задание через 10 секунд после старта) и @Scheduled (сводка каждые 30 секунд);
- хранение в H2 через JPA с уникальным индексом по паре тип+значение;
- выдача из H2 с фильтрацией и сортировкой через parallelStream;
- юнит-тесты (парсер, выдача с мок-репозиторием, загрузка контекста), запускаются командой mvnw test. (`.\mvnw.cmd test`)
