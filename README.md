# LangChain4j Coding Agent

Java 21 + Gradle + Jetty + RESTEasy + Guice + JDBI + Postgres + LangChain4j.

The agent exposes a streaming NDJSON endpoint and has three tools:

1. `execute_bash`
2. `web_search`
3. `fetch_url`

## Run

```bash
cp .env.example .env
docker compose up --build
```

## Health

```bash
curl http://localhost:8080/v1/health | jq
```

## Streaming chat

```bash
curl -N http://localhost:8080/v1/chat/stream   -H 'Content-Type: application/json'   -d '{
    "sessionId": "demo",
    "message": "Inspect the workspace, create hello.py that prints hello from LangChain4j, run it, and summarize what happened."
  }'
```

The response is `application/x-ndjson`, one JSON event per line.

## Inspect run history

```bash
curl http://localhost:8080/v1/runs | jq
```
