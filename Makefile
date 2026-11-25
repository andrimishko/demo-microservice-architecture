MVN = docker run --rm -v "$(CURDIR)":/src -v soa-lab3-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -q -B

.PHONY: build test up down ps logs nats-tail features-tail
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
# stream info + every stored message of DETECTION (`nats stream view` needs a TTY, so loop over sequence numbers)
nats-tail:
	docker run --rm --network soa-lab3_internal natsio/nats-box sh -c \
	  'N="nats -s nats://nats:4222"; $$N stream info DETECTION; \
	   for i in $$(seq 1 $$($$N stream info DETECTION -j | jq .state.last_seq)); do $$N stream get DETECTION $$i; done'
# last N (default 5) features.built messages: walk the stream backwards from the last sequence number
features-tail:
	docker run --rm --network soa-lab3_internal natsio/nats-box sh -c \
	  'N="nats -s nats://nats:4222"; n=0; i=$$($$N stream info DETECTION -j | jq .state.last_seq); \
	   while [ $$n -lt $(or $(N),5) ] && [ $$i -gt 0 ]; do m=$$($$N stream get DETECTION $$i); \
	     case "$$m" in *"on Subject features.built"*) echo "$$m"; n=$$((n+1));; esac; i=$$((i-1)); done'
