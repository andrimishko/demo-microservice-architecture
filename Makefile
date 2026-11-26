MVN = docker run --rm -v "$(CURDIR)":/src -v soa-lab3-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -q -B

.PHONY: lint-contracts build test e2e reset up down ps logs nats-tail features-tail scores-tail fake-feedback alerts feedback-stats
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
# last N (default 5) score.computed messages, same backwards walk as features-tail
scores-tail:
	docker run --rm --network soa-lab3_internal natsio/nats-box sh -c \
	  'N="nats -s nats://nats:4222"; n=0; i=$$($$N stream info DETECTION -j | jq .state.last_seq); \
	   while [ $$n -lt $(or $(N),5) ] && [ $$i -gt 0 ]; do m=$$($$N stream get DETECTION $$i); \
	     case "$$m" in *"on Subject score.computed"*) echo "$$m"; n=$$((n+1));; esac; i=$$((i-1)); done'
# DEPRECATED since stage 5 (use POST /v1/feedback of operator-feedback): publishes feedback.submitted by hand.
# make fake-feedback VERDICT=FALSE_POSITIVE|MISSED|CONFIRMED [VECTOR='{"vector":[...],"feature_names":[...]}']
fake-feedback:
	docker run --rm --network soa-lab3_internal -v "$(CURDIR)/scripts":/scripts -e VERDICT="$(VERDICT)" -e VECTOR='$(VECTOR)' \
	  natsio/nats-box sh /scripts/fake-feedback.sh
# alerts the mock Alertmanager has received (from the host; the mock never resolves them)
alerts:
	curl -s localhost:8090/api/v2/alerts
# feedback statistics of one node: make feedback-stats [NODE=node-1] [PERIOD=PT1H]
feedback-stats:
	docker run --rm --network soa-lab3_internal curlimages/curl -s \
	  "http://operator-feedback:8080/v1/feedback/stats?node_id=$(or $(NODE),node-1)&period=$(or $(PERIOD),PT1H)"

# validate the OpenAPI (Redocly) and AsyncAPI contracts in contracts/ (warnings are allowed, errors fail)
lint-contracts:
	docker run --rm -v "$(CURDIR)/contracts":/spec -w /spec redocly/cli:1.34.5 lint openapi/*.yaml
	docker run --rm -e ASYNCAPI_METRICS=false -v "$(CURDIR)/contracts":/spec asyncapi/cli:3.4.1 validate /spec/asyncapi.yaml

# automated end-to-end test against the running stack (through the gateway); `make test` skips it
e2e:
	docker run --rm -v "$(CURDIR)":/src -v soa-lab3-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 \
	  mvn -B -pl e2e -DexcludedGroups=none -Dgroups=e2e -De2e.gateway=http://host.docker.internal:8000 \
	  -De2e.mock=http://host.docker.internal:8090 test

# wipe ONLY this project's containers and volumes (soa-lab3), rebuild, wait until healthy, seed demo data
reset:
	docker compose -p soa-lab3 down -v
	docker compose up -d --build
	demo/wait-healthy.sh
	demo/seed.sh
