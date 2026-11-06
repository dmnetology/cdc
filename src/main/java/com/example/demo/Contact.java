package com.example.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Контакт организации, собранный краулером.
 * Пара тип плюс значение уникальна, один и тот же контакт не заносится в базу повторно.
 */
@Entity
@Table(name = "contacts", uniqueConstraints = @UniqueConstraint(columnNames = {"type", "contact_value"}))
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Значение перечисления ContactType в виде строки. */
    @Column(nullable = false, length = 20)
    private String type;

    /** Сам контакт (номер телефона, email или почтовый адрес). Колонка не названа value, это зарезервированное слово в H2. */
    @Column(name = "contact_value", nullable = false, length = 300)
    private String value;

    /** Страница, на которой контакт найден. */
    @Column(length = 500)
    private String sourceUrl;

    /** Название задания, собравшего этот контакт. */
    @Column(length = 120)
    private String taskLabel;

    /** Момент занесения в базу. */
    @Column(nullable = false)
    private LocalDateTime foundAt;

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getTaskLabel() {
        return taskLabel;
    }

    public void setTaskLabel(String taskLabel) {
        this.taskLabel = taskLabel;
    }

    public LocalDateTime getFoundAt() {
        return foundAt;
    }

    public void setFoundAt(LocalDateTime foundAt) {
        this.foundAt = foundAt;
    }
}
