package ua.lab3.inference;

import java.util.List;
import java.util.OptionalInt;

/** Port: where model versions and the "active" pointer live (adapter: MinIO). */
public interface ModelRepository {
    List<Integer> versions();

    Model load(int version);

    /** Stores the model under its own version number. */
    void save(Model model);

    OptionalInt activeVersion();

    void setActive(int version);
}
