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
anomaly-assessment/      S4: класифікація оцінок за порогами, алерти й ескалація (етап 5)
operator-feedback/       S5: відгуки оператора, API + події feedback.submitted (етап 5)
common/                  спільна бібліотека: EventEnvelope, Subjects, NATS auto-config, EventPublisher, health "nats"
docker/service.Dockerfile  багатоетапна збірка будь-якого модуля (ARG MODULE)
docker-compose.yml       інфраструктура (проєкт soa-lab3)
infra/                   init.sql для Postgres, default.conf.template для nginx (шлюз, етап 6)
contracts/               OpenAPI 3.1 (4 REST-сервіси) і AsyncAPI 3.0 (події NATS), етап 6
e2e/                     автоматичний наскрізний тест через шлюз (JUnit 5), етап 7
demo/                    сценарії демонстрації (bash) і скрипти reset/seed, етап 7
```

## Запуск

```bash
cp .env.example .env   # вже створено
make up                # docker compose up -d --build
make ps
make down              # зупинити (дані зберігаються у томах)
```
