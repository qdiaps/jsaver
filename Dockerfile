FROM eclipse-temurin:21-jdk-noble AS builder

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-noble AS runtime

LABEL maintainer="qdiaps"
LABEL description="jsaver - Telegram Bot"

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    ffmpeg \
    quickjs \
    python3 \
    python3-pip \
    python3-venv \
    && rm -rf /var/lib/apt/lists/*

# Setup isolated Python virtual environment for CLI tools (yt-dlp, gallery-dl, curl_cffi)
ENV VIRTUAL_ENV=/opt/venv
RUN python3 -m venv $VIRTUAL_ENV
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir \
        yt-dlp \
        gallery-dl \
        curl_cffi

RUN groupadd -g 10001 appuser && \
    useradd -u 10001 -g appuser -m -s /bin/bash appuser

WORKDIR /app

RUN mkdir -p /app/data /app/logs && \
    chown -R appuser:appuser /app

COPY --from=builder --chown=appuser:appuser /build/target/jsaver.jar /app/jsaver.jar

USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV APP_ENV="prod"
ENV JSAVER_WORK_DIR="/app/data"
ENV LOG_DIR="/app/logs"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/jsaver.jar"]
