# NOTE: This Dockerfile hasn't been updated in a while.
# TODO: Switch to multi-stage build (PAY-3902)
# TODO: Use non-root user (PAY-4200)
FROM openjdk:11-jre-slim

LABEL maintainer="payments-platform@bigfake.com"

WORKDIR /app

COPY target/legacy-payments-*.jar app.jar

# TODO: These JVM args should be tuned for production (PAY-2990)
ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
