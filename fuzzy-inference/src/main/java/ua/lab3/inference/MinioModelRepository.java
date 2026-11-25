package ua.lab3.inference;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ua.lab3.common.EventEnvelope;

/** Layout: models/v{n}/model.json and models/active.json = {"version": n}. */
@Component
public class MinioModelRepository implements ModelRepository {
    private static final String ACTIVE = "active.json";
    private static final Pattern KEY = Pattern.compile("v(\\d+)/model\\.json");

    private final MinioClient minio;
    private final String bucket;

    MinioModelRepository(@Value("${minio.endpoint}") String endpoint, @Value("${minio.access-key}") String accessKey,
                         @Value("${minio.secret-key}") String secretKey, @Value("${minio.bucket}") String bucket) {
        this.minio = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    @Override
    public List<Integer> versions() {
        List<Integer> out = new ArrayList<>();
        try {
            for (Result<Item> r : minio.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix("v").recursive(true).build())) {
                Matcher m = KEY.matcher(r.get().objectName());
                if (m.matches()) out.add(Integer.parseInt(m.group(1)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO list failed", e);
        }
        return out.stream().sorted().toList();
    }

    @Override
    public Model load(int version) {
        try {
            return Model.parse(read("v" + version + "/model.json"));
        } catch (Exception e) {
            throw new IllegalStateException("cannot load model v" + version, e);
        }
    }

    @Override
    public void save(Model model) {
        put("v" + model.version() + "/model.json", write(model));
    }

    @Override
    public OptionalInt activeVersion() {
        try {
            return OptionalInt.of(EventEnvelope.MAPPER.readTree(read(ACTIVE)).path("version").asInt());
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) return OptionalInt.empty();
            throw new IllegalStateException("MinIO read failed", e);
        } catch (Exception e) {
            throw new IllegalStateException("MinIO read failed", e);
        }
    }

    @Override
    public void setActive(int version) {
        put(ACTIVE, write(Map.of("version", version)));
    }

    private byte[] read(String key) throws Exception {
        try (InputStream in = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return in.readAllBytes();
        }
    }

    private void put(String key, byte[] json) {
        try {
            minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(json), json.length, -1).contentType("application/json").build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO write of " + key + " failed", e);
        }
    }

    private static byte[] write(Object o) {
        try {
            return EventEnvelope.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
