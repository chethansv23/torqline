.DEFAULT_GOAL := help
COMPOSE := docker compose
MVN_DOCKER := docker run --rm -v "$(CURDIR)":/src -v torqline-m2:/root/.m2 \
	-v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
	-w /src maven:3.9-eclipse-temurin-21

help: ## Show available commands
	@grep -E '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

up: ## Build and start everything (infra, 5 services, web UI on :3000)
	$(COMPOSE) up -d --build --wait

infra: ## Start only Postgres, Redis and Kafka (run the services from your IDE)
	$(COMPOSE) up -d --wait postgres redis kafka

tools: ## Start Kafka UI on http://localhost:8090
	$(COMPOSE) --profile tools up -d kafka-ui

ui-dev: ## Run the web UI with hot reload on http://localhost:5173 (needs Node)
	cd web && npm install && npm run dev

demo: ## Run the end-to-end walkthrough against the gateway
	./scripts/demo.sh

loadtest: ## Race 50 bookings for one slot and measure availability reads with k6
	docker run --rm -i -v "$(CURDIR)/loadtest":/scripts grafana/k6 run /scripts/booking.js

test: test-backend test-web ## Run all backend and frontend tests

test-backend: ## Backend tests + JaCoCo coverage (uses local JDK 21 if present, otherwise Maven in Docker)
	@if java -version >/dev/null 2>&1; then ./mvnw -B verify; else $(MVN_DOCKER) mvn -B verify; fi

test-web: ## Frontend tests (uses local Node if present, otherwise Node in Docker)
	@if command -v npm >/dev/null 2>&1; then cd web && npm ci --no-audit --no-fund && npm test; \
	else docker run --rm -v "$(CURDIR)/web":/app -w /app node:22-alpine sh -c "npm ci && npm test"; fi

coverage-web: ## Frontend coverage report in web/coverage
	cd web && npm run coverage

logs: ## Follow service logs
	$(COMPOSE) logs -f appointment-service repair-order-service parts-inventory-service notification-service gateway

ps: ## Show container status
	$(COMPOSE) ps

down: ## Stop everything (keeps data)
	$(COMPOSE) --profile tools down

reset: ## Stop everything and delete all data
	$(COMPOSE) --profile tools down -v

.PHONY: help up infra tools ui-dev demo loadtest test test-backend test-web coverage-web logs ps down reset
