# Vegam City Smart Traffic Management System

Full-stack Java web application based on the original CLI program.

## Stack

- Java 25 LTS+
- Spring Boot 4.0
- Spring Web REST API
- Spring Data JPA/Hibernate
- MySQL database
- HTML, CSS and Vanilla JavaScript

## Database-backed data

The application persists for the running demonstration:

- Four road names and their current vehicle counts in `roads`
- Emergency dispatch history in `emergency_vehicles`
- Every traffic modification in `traffic_events`

The graph, traffic multiplier `HashMap`, traffic event `Queue`, and Dijkstra min-heap remain in the service because they represent the live routing algorithm. MySQL stores the current road counts, traffic events, and emergency dispatch history across application restarts.

## Run locally

Docker Desktop, Maven, and Java 25+ are required. Start MySQL first:

```powershell
docker compose up -d mysql
mvn spring-boot:run
```

Open <http://localhost:8080>.

MySQL is created automatically with these settings:

```text
JDBC URL: jdbc:mysql://localhost:3306/smart_traffic
Username: root
Password: root
```

Spring Boot creates or updates the required tables automatically.

## Database console

Use MySQL Workbench, DBeaver, or the MySQL CLI and connect to:

```text
Host: localhost
Port: 3306
Database: smart_traffic
User: root
Password: root
```

## Live updates

The browser opens `GET /api/stream` using Server-Sent Events. Every successful traffic update or emergency dispatch:

1. Saves a JPA entity to MySQL.
2. Recalculates the current dashboard/Dijkstra data.
3. Immediately publishes a `traffic-update` event to connected browsers.

## REST endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/dashboard` | Current roads and dispatch history |
| PUT | `/api/traffic` | Save vehicle counts |
| POST | `/api/emergency` | Dispatch an emergency vehicle |
| POST | `/api/route` | Calculate an optimal route |
| GET | `/api/stream` | Live Server-Sent Events dashboard stream |

The frontend is served from `src/main/resources/static/index.html`, so no Node.js, npm or React setup is required.
