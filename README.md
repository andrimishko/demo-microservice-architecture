# SOA Lab 3: розподілена система виявлення аномалій

Навчальний прототип: хореографія сервісів через брокер повідомлень NATS JetStream.
Усі дані імітовані (сервіс `mock-observability` відтворює HTTP API Prometheus / OpenSearch / Alertmanager).

## Структура

```
pom.xml                  батьківський Maven-POM (Spring Boot 3.4.1, Java 21)
common/                  спільна бібліотека: EventEnvelope, Subjects, NATS auto-config, EventPublisher, health "nats"
docker/service.Dockerfile  багатоетапна збірка будь-якого модуля (ARG MODULE)
docker-compose.yml       інфраструктура (проєкт soa-lab3)
infra/                   init.sql для Postgres, nginx.conf
```

Сервіси S1-S5 та `mock-observability` додаються на наступних етапах.

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
| 8222 | NATS monitoring |
| 9001 | MinIO console |

Порти 80, 8080, 8083, 8084, 9090, 9200, 3000, 5601 зайняті іншим стеком (`anomaly-research`).
