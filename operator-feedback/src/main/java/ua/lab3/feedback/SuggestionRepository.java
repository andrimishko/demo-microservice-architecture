package ua.lab3.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionRepository extends JpaRepository<SuggestionEntity, String> {}
