package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Хранилище контактов в базе H2.
 */
public interface ContactRepository extends JpaRepository<Contact, Long> {

    /** True, если контакт с таким типом и значением уже занесён в базу. */
    boolean existsByTypeIgnoreCaseAndValueIgnoreCase(String type, String value);
}
