FROM eclipse-temurin:17-alpine

RUN adduser -h /opt/app -H -D app && \
    mkdir -p /opt/app && \
    chown app:app /opt/app

USER app:app

WORKDIR /opt/app

COPY build/libs/DiscordBot.jar DiscordBot.jar

CMD ["java", "-jar", "DiscordBot.jar"]
