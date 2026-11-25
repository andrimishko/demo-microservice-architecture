# SOA Lab 3: розподілена система виявлення аномалій

Навчальний прототип: хореографія сервісів через брокер повідомлень NATS JetStream.
Усі дані імітовані (сервіс `mock-observability` відтворює HTTP API Prometheus / OpenSearch / Alertmanager).

## Структура

```
pom.xml                  батьківський Maven-POM (Spring Boot 3.4.1, Java 21)
mock-observability/      імітація HTTP API Prometheus / OpenSearch / Alertmanager (етап 2)
node-registry/           S1: реєстр вузлів, REST + події node.changed (етап 2)
feature-aggregator/      S2: вектори ознак на часове вікно, події features.built (етап 3)
fuzzy-inference/       S3: нейро-нечітка модель (ролі inference і adaptation), версії моделі в MinIO (етап 4)
common/                  спільна бібліотека: EventEnvelope, Subjects, NATS auto-config, EventPublisher, health "nats"
docker/service.Dockerfile  багатоетапна збірка будь-якого модуля (ARG MODULE)
docker-compose.yml       інфраструктура (проєкт soa-lab3)
infra/                   init.sql для Postgres, nginx.conf
```

Сервіси S2-S5 додаються на наступних етапах.

## Запуск

```bash
cp .env.example .env   # вже створено
make test              # Maven у Docker (локально Maven не потрібен)
make up                # docker compose up -d --build
make ps
make down
```

## Що дає етап 1

- Maven-каркас і модуль `common`: конверт події `{event_id, event_type, occurred_at, schema_version, payload}`,
  константи subjects, ідемпотентне створення стріму `DETECTION` (file, дедуплікація 2 хв), публікація з заголовком `Nats-Msg-Id`.
- Інфраструктура: NATS JetStream, Postgres (схеми `registry`, `feedback`), MinIO (бакет `models`), nginx (`/health`).

## Порти хоста

| Порт | Сервіс |
|------|--------|
| 8000 | nginx (gateway) |
| 8090 | mock-observability (керування сценаріями для демо) |
| 8222 | NATS monitoring |
| 9001 | MinIO console |

Порти 80, 8080, 8083, 8084, 9090, 9200, 3000, 5601 зайняті іншим стеком (`anomaly-research`).

## Що дає етап 2

### mock-observability

Один сервіс, що відповідає як три зовнішні системи (на наступних етапах сервіси ходять до нього через anti-corruption шар,
наче до справжніх). Відомі вузли задаються змінною `MOCK_NODES` (за замовчуванням `node-1,node-2,node-3`), для інших порожні відповіді.

| API | Ендпоїнти |
|-----|-----------|
| Prometheus | `GET /api/v1/query?query=`, `GET /api/v1/query_range?query=&start=&end=&step=`; метрики `up`, `cpu_usage`, `memory_used_ratio`, `http_latency_p95_seconds`, `http_error_rate`; селектор лише `metric` або `metric{instance="node-1"}` |
| OpenSearch | `POST /{index}/_search` (з тіла береться `node_id` і діапазон `@timestamp`, інакше остання хвилина) |
| Alertmanager | `POST /api/v2/alerts` (зберігає в пам'яті і логує), `GET /api/v2/alerts` |

Сценарій кожного вузла (у пам'яті, за замовчуванням `NORMAL`); значення = базова лінія сценарію ± 5 % шуму:

| Сценарій | Метрики | Логи |
|----------|---------|------|
| `NORMAL` | норма | кілька INFO |
| `ANOMALY` | високі cpu, затримка, частка помилок | багато ERROR |
| `NOISY_LOGS` | норма | сплеск безпечних WARN (хибнопозитивний випадок) |
| `CPU_SPIKE_BENIGN` | високий cpu, решта норма | норма |

```bash
curl localhost:8090/scenario                                   # сценарії всіх вузлів
curl -X POST localhost:8090/scenario -H 'Content-Type: application/json' \
     -d '{"node_id":"node-1","scenario":"ANOMALY"}'
curl 'localhost:8090/api/v1/query?query=cpu_usage%7Binstance%3D%22node-1%22%7D'
curl -X POST localhost:8090/logs/_search -H 'Content-Type: application/json' \
     -d '{"query":{"bool":{"filter":[{"term":{"node_id":"node-1"}}]}}}'
```

### S1 node-registry

Зберігає вузли в Postgres (схема `registry`, міграції Flyway). Прямо з хоста порт не відкрито (з'явиться через nginx на етапі 6).
Помилки віддаються як RFC 9457 `application/problem+json`. Після кожної зміни публікується `node.changed`
з `payload {op: created|updated|deleted, node_id, type, thresholds, enabled}`; подія йде після коміту транзакції
(сервіс не `@Transactional`, тому `repository.save` комітить сам). Недолік: якщо публікація впаде, запис уже збережено, а подію втрачено (лікується transactional outbox).

| Запит | Відповіді |
|-------|-----------|
| `POST /v1/nodes` `{node_id, name, type, thresholds:{warning, anomaly}}` | 201; 400 (порожні поля, невідомий тип, пороги поза [0,1] або `warning >= anomaly`); 409 вузол існує; 422 Prometheus не знає вузла (`up{instance="<id>"}` порожній) |
| `GET /v1/nodes?enabled=true\|false` | 200 |
| `GET /v1/nodes/{id}` | 200 / 404 |
| `PATCH /v1/nodes/{id}` `{thresholds?, enabled?}` | 200 / 400 / 404 |
| `DELETE /v1/nodes/{id}` | 204 / 404 |
| `GET /v1/nodes/{id}/queries` | 200 `{метрика: PromQL}` / 404 |
| `GET /v1/node-types` | 200, типи `SPRING_BOOT`, `POSTGRES`, `LINUX` з їхніми метриками |

Спробувати зсередини мережі:

```bash
c() { docker run --rm --network soa-lab3_internal curlimages/curl -s -w '\n[%{http_code}]\n' "$@"; }
c -X POST http://node-registry:8080/v1/nodes -H 'Content-Type: application/json' \
  -d '{"node_id":"node-1","name":"API","type":"SPRING_BOOT","thresholds":{"warning":0.5,"anomaly":0.8}}'
c http://node-registry:8080/v1/nodes
c http://node-registry:8080/v1/nodes/node-1/queries
make nats-tail      # інформація про стрім DETECTION і всі збережені повідомлення (заголовок Nats-Msg-Id)
```

Стрім `DETECTION` створюється при старті сервісу під час створення біна `JetStream` у `common`, тобто до першої публікації.
Health: `/actuator/health` (групи `liveness`, `readiness`; readiness включає `db` і `nats`).
