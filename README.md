# Zelus RSPS

[![Release](https://img.shields.io/github/v/release/zelusrsps/zelus?style=flat-square)](https://github.com/zelusrsps/zelus/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/zelusrsps/zelus/deploy.yml?branch=main&style=flat-square)](https://github.com/zelusrsps/zelus/actions)
[![License](https://img.shields.io/badge/license-Proprietary-blue.svg?style=flat-square)](#license)

**Zelus** is a modern, high-performance, semi-custom Old School RuneScape private server (RSPS) featuring custom bosses, unique raids, optimized network pipelines, an integrated webstore engine, and an automated deployment stack.

---

## 📑 Table of Contents

- [Key Features](#-key-features)
- [Architecture & Tech Stack](#-architecture--tech-stack)
- [Repository Structure](#-repository-structure)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Local Development Setup](#local-development-setup)
- [Configuration & Environment](#-configuration--environment)
- [CI/CD & Deployment Pipeline](#-cicd--deployment-pipeline)
- [Integrations & Webhooks](#-integrations--webhooks)
- [Contributing & Code Guidelines](#-contributing--code-guidelines)
- [License](#-license)

---

## ✨ Key Features

- **High-Performance Game Core:** Modular game engine utilizing low-latency entity synchronization, optimized dynamic map loading, and multi-threaded tick processors.
- **Custom Content Engine:** Custom Raids, Boss Encounters, Pet Recolor & Upgrade systems, and balanced drop-table pipelines.
- **Automated Webhooks & Delivery:** Instant in-game fulfillment (`::claim`) powered by transactional webhooks and row-locking idempotency safety nets.
- **Automated Vote Callbacks:** Real-time top-list vote verification and point dispatch.
- **Discord Bot & Event Broadcasts:** Real-time rare-drop alerts, player milestones, and moderation feeds decoupled from general collection log logs.

---

## 🏗 Architecture & Tech Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Game Server** | Java 17 / Gradle | Core game engine, packet decoding (`rsprot`), world states |
| **Backend API** | Python 3.11 / FastAPI | Webhooks, authentication, top-list callbacks, claim queuing |
| **Frontend Portal** | React / Vite / TailwindCSS | Player dashboard, vote gateway, catalog showcase, webstore |
| **Database** | PostgreSQL | Player persistence, transactions, pending claims, vote logs |
| **Orchestration** | Docker Swarm / Compose | High-availability container cluster and zero-downtime rolling deploys |
| **CI/CD** | GitHub Actions | Automated build, test, multi-stage packaging, and gated deploy |

---

## 📁 Repository Structure

```plaintext
zelus/
├── .github/
│   └── workflows/              # GitHub Actions build and deploy pipelines
├── game-server/                # Java core game server
│   ├── src/main/java/          # Server engine, scripts, plugins, network handlers
│   ├── data/                   # JSON configs, item/NPC definitions, drop tables
│   └── build.gradle            # Gradle build definitions
├── backend-api/                # FastAPI web & webhook microservices
│   ├── app/
│   │   ├── api/                # API routes (webhooks, votes, auth, claims)
│   │   ├── core/               # App configuration, security, database sessions
│   │   └── models/             # SQLAlchemy ORM schemas
│   ├── Dockerfile
│   └── requirements.txt
├── web-portal/                 # React web client and store
│   ├── src/                    # UI components, pages, context providers
│   ├── package.json
│   └── vite.config.js
├── docker-compose.yml          # Local container definitions
└── README.md
