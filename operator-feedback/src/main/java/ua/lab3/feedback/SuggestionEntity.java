package ua.lab3.feedback;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "feedback", name = "suggestion")
public class SuggestionEntity {
    @Id
    private String id;
    private String text;
    private String author;
    private Instant createdAt;

    protected SuggestionEntity() {}

    public SuggestionEntity(String id, String text, String author, Instant createdAt) {
        this.id = id;
        this.text = text;
        this.author = author;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String text() { return text; }
}
