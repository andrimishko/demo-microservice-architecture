MVN = docker run --rm -v "$(CURDIR)":/src -v soa-lab3-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -q -B

.PHONY: build test up down ps logs
build:
	$(MVN) verify
test:
	$(MVN) test
up:
	docker compose up -d --build
down:
	docker compose down
ps:
	docker compose ps
logs:
	docker compose logs -f --tail=100
