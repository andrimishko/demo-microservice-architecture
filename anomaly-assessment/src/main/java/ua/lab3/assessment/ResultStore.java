package ua.lab3.assessment;

/** Port: where classification results are kept. */
public interface ResultStore {
    void save(Classified result);
}
