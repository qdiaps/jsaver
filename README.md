# jsaver

[![CI](https://github.com/qdiaps/jsaver/actions/workflows/ci.yml/badge.svg)](https://github.com/qdiaps/jsaver/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

A multi-platform Telegram bot that automatically detects media links in private messages and group chats, extracts the content without watermarks, and sends it straight back to the conversation.

## Features
* *Broad platform support:* Works across dozens of popular social networks, video hostings, and image boards — designed to easily scale to hundreds of sources.
* *All media formats:* Fetches videos, separate audio tracks, single images, and multi-photo carousels (delivered as clean Telegram media albums).
* *Built for group chats:* Silently spots links in the flow of conversation and replies with the media without cluttering the chat.
* *High-quality & large files:* Handles heavy, high-resolution videos seamlessly without aggressive compression or tight size limits.

---

## Prerequisites

1. **Java 21 LTS** or later.
2. Installed CLI tools (for local/standalone runs):
   * `ffmpeg`, `ffprobe`
   * `python3`, `python3-pip`
   * `yt-dlp` (`pip install yt-dlp`)
   * `gallery-dl` (`pip install gallery-dl`)
   * `curl_cffi` (`pip install curl_cffi`)
   * `quickjs` (`qjs`)
3. Docker and Docker Compose (optional, for containerized deployment).

---

## Environment Configuration

Copy the example configuration file:
```bash
cp .env.example .env
```

Configure variables in `.env`:
* `TELEGRAM_BOT_TOKEN`: Bot token from [@BotFather](https://t.me/BotFather).
* `TELEGRAM_API_ID` & `TELEGRAM_API_HASH`: Obtained from [my.telegram.org](https://my.telegram.org) (required for the local Bot API server).
* `ALLOWED_TELEGRAM_USERS`: Comma-separated Telegram User IDs permitted to execute commands.
* `APP_ENV`: Environment profile (`dev` or `prod`).

---

## Running the Application

### Option 1: Docker Compose (Recommended for Production)

Launches both the local Telegram Bot API Server and the bot:

```bash
# Build images and start services in the background
docker compose up -d --build

# View application logs
docker compose logs -f bot

# Stop services
docker compose down
```

### Option 2: Standalone Execution without Docker (Linux / macOS)

1. Build the standalone package with Maven Wrapper:
   ```bash
   ./mvnw clean package
   ```
2. Run the executable Fat JAR:
   ```bash
   java -jar target/jsaver.jar
   ```
   *(The application automatically reads configuration from `.env` in the current working directory)*.

### Option 3: Running on Android (Termux)

The project is decoupled from x86-specific containers and can run natively in Android's Termux environment:

1. Install prerequisites in Termux:
   ```bash
   pkg update && pkg upgrade
   pkg install openjdk-21 ffmpeg python quickjs
   pip install yt-dlp gallery-dl curl_cffi
   ```
2. Clone the repository and build the JAR (or transfer a pre-built `jsaver.jar`):
   ```bash
   ./mvnw clean package -DskipTests
   ```
3. Run the bot:
   ```bash
   java -jar target/jsaver.jar
   ```
   
---

## Development & Testing

### Maven Commands

```bash
# Run unit tests (JUnit 5 + Mockito + AssertJ)
./mvnw clean test

# Complete build, testing, and Fat JAR packaging
./mvnw clean verify

# Quick packaging skipping test execution
./mvnw package -DskipTests
```
