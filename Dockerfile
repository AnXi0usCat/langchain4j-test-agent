FROM gradle:8.14.3-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon clean installDist

FROM eclipse-temurin:21-jre-alpine

# UPDATED: Added python3 to the apk install command
RUN apk add --no-cache bash coreutils python3

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /workspace \
    && chown -R app:app /workspace /app

COPY --from=build /home/gradle/project/build/install/lc4j-coding-agent/ ./

USER app
EXPOSE 8080
CMD ["bin/lc4j-coding-agent"]
