package ua.lab3.registry;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeRepository extends JpaRepository<Node, String> {
    List<Node> findByEnabled(boolean enabled);
}
