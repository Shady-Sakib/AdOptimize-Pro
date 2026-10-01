# AdOptimize Pro — Online Advertisement Optimizer

A complete web application where advertisers create and manage ad campaigns, set budgets, schedule
delivery, track performance and receive optimization suggestions — plus a secure admin panel for user
management, ad approval, moderation, revenue tracking and report generation.

Built with **Spring Boot 4.1.1**, **Spring MVC**, **Spring Security**, **Thymeleaf**, **Spring JDBC
(JdbcClient)**, **Bean Validation**, **Lombok** and **MySQL**. The frontend is plain HTML/CSS/JavaScript
(no frontend framework, no CDN dependencies) served by Thymeleaf.

---

## Table of contents

1. [What the application does](#1-what-the-application-does)
2. [Quick start](#2-quick-start)
3. [Demo accounts](#3-demo-accounts)
4. [Configuration](#4-configuration)
5. [Project structure](#5-project-structure)
6. [How it works](#6-how-it-works)
   - [Request flow](#61-request-flow)
   - [Authentication and the admin group code](#62-authentication-and-the-admin-group-code)
   - [Campaign lifecycle](#63-campaign-lifecycle)
   - [Money: the wallet model](#64-money-the-wallet-model)
   - [The traffic simulator](#65-the-traffic-simulator)
   - [The optimizer](#66-the-optimizer)
   - [Content moderation](#67-content-moderation)
   - [Notifications](#68-notifications)
   - [Reports](#69-reports)
7. [Database schema](#7-database-schema)
8. [Validation rules](#8-validation-rules)
9. [Security notes](#9-security-notes)
10. [API reference](#10-api-reference)
11. [Guided demo walkthrough](#11-guided-demo-walkthrough)
12. [Tests](#12-tests)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. What the application does

### Advertiser side (`/dashboard`)

| Section | What it does |
|---|---|
| **Overview** | KPIs (impressions, clicks, CTR, spend) for the last 7/30/90 days with period-over-period change, an impressions/clicks trend chart, a campaign-status doughnut and a recent-campaigns table. |
| **Campaigns** | Create, edit, pause, resume and delete campaigns; filter by status; open a detail view with a daily chart, keywords and applied optimizations. |
| **Optimizer** | A 0–100 performance score per campaign plus prioritized suggestions (keywords, ad format, peak-hour scheduling, budget pacing, conversion advice). Applicable suggestions can be applied with one click. |
| **Budget** | Balance, reserved funds, available funds and total spend; overall budget usage; monthly spend chart; per-campaign budget table. |
| **Payments** | Add funds with a validated card form (live card preview); transaction history. |
| **Notifications** | Approvals, rejections, budget alerts and support replies, with unread badges. |
| **Profile** | Update name/email/company, change password, account stats. |
| **Support** | Open support tickets and read admin replies. |

### Admin side (`/admin`)

| Section | What it does |
|---|---|
| **Dashboard** | Platform totals, 6-month impressions and payments charts, pending-approval queue, open tickets. |
| **Analytics** | Platform-wide impressions, clicks, CTR, conversions; monthly trends; audience distribution; payments vs ad spend. |
| **Users** | Search/filter advertisers, view a full account profile (wallet, campaigns, payments, tickets), activate/deactivate accounts. |
| **Ad Management** | Review every campaign; approve, reject (with a reason the advertiser sees) or remove. |
| **Moderation** | Campaigns flagged by the content filter, blocked-word list, clear-flag and re-scan actions. |
| **Revenue** | Total payments, platform earnings (fee %), transaction count, monthly chart, transaction table. |
| **Reports** | Seven CSV reports: performance, revenue, users, campaigns, audience, optimization adoption and a full platform report. |
| **Complaints** | Reply to and resolve support tickets, or reopen them. |
| **Settings** | CPC/CPM rates, platform fee, min budget, max daily budget, peak hours, auto-approve, content filter, blocked words, budget alerts, traffic simulator on/off and a "run simulation now" button. |

---

## 2. Quick start

### Prerequisites

- **JDK 17 or newer** (the `pom.xml` targets Java 17, which Spring Boot 4 requires as a minimum; newer JDKs work too)
- **MySQL 8.x** running on `localhost:3306`
- No Maven installation needed — the Maven Wrapper is included

### Run it

```bash
# 1. unzip and enter the project
cd ad-optimizer

# 2. start it (Linux/macOS)
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

If your MySQL credentials are not `root` / `root`:

```bash
DB_USERNAME=root DB_PASSWORD=yourpassword ./mvnw spring-boot:run
```

Then open **<http://localhost:8080>**.

You do **not** need to create the database or any tables by hand. The JDBC URL contains
`createDatabaseIfNotExist=true`, and `src/main/resources/schema.sql` creates every table on start.
The script is fully idempotent (`CREATE TABLE IF NOT EXISTS`, guarded index creation), so restarting
never destroys data.

On the very first start — and only when the `users` table is empty — the application seeds demo data:
5 users, 11 campaigns, ~90 days of daily statistics, payments, notifications and support tickets.
Set `app.seed.enabled=false` in `application.properties` for an empty database.

### Build a jar

```bash
./mvnw clean package
java -jar target/ad-optimizer-1.0.0.jar
```

---

## 3. Demo accounts

| Role | Name | Email | Password | Notes |
|---|---|---|---|---|
| Advertiser | Alex Morgan | `demo@adpro.com` | `demo123` | The main demo account — sign in at `/` |
| Advertiser | Priya Nair | `priya@freshcart.io` | `password123` | |
| Advertiser | Daniel Okafor | `daniel@voltgear.com` | `password123` | |
| Advertiser | Sofia Rossi | `sofia@nestliving.co` | `password123` | **Deactivated** — for testing the blocked-sign-in path |
| **Admin** | Platform Admin | `admin@adpro.com` | `admin123` | Sign in at `/admin/login` with group code **`group 5`** |

Both login pages show an "Auto-fill Demo Credentials" link while seeding is enabled.

Administrators **cannot** sign in through the advertiser form and advertisers cannot sign in through the
admin portal; each page redirects you to the right one.

---

## 4. Configuration

All settings live in `src/main/resources/application.properties` and can be overridden with environment
variables without editing the file.

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:mysql://localhost:3306/ad_optimizer?createDatabaseIfNotExist=true…` | Database URL |
| `spring.datasource.username` | `DB_USERNAME` | `root` | Database user |
| `spring.datasource.password` | `DB_PASSWORD` | `root` | Database password |
| `app.admin.group-code` | `ADMIN_GROUP_CODE` | `group 5` | Code required for admin sign-in and admin registration |
| `app.seed.enabled` | — | `true` | Seed demo data when the database is empty |
| `app.simulation.interval-ms` | — | `20000` | How often the traffic simulator runs |
| `app.simulation.initial-delay-ms` | — | `15000` | Delay before the first simulator run |
| `app.security.max-login-attempts` | — | `5` | Failed sign-ins before an email is locked |
| `app.security.lockout-minutes` | — | `5` | Lockout duration |
| `server.port` | — | `8080` | HTTP port |

Runtime business settings (CPC, CPM, platform fee, minimum budget, maximum daily budget, peak hours,
auto-approve, content filter, blocked words, budget alerts, simulator on/off) are **stored in the
database** and edited in the admin **Settings** page — no restart required.

---

## 5. Project structure

```
ad-optimizer/
├── mvnw, mvnw.cmd, .mvn/            Maven Wrapper (no Maven install needed)
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/adoptimizer/    124 Java files
    │   │   ├── AdOptimizerApplication.java
    │   │   ├── config/              AppProperties, SecurityConfig, DataSeeder
    │   │   ├── security/            UserDetailsService, active-account filter,
    │   │   │                        login-attempt lockout, JSON auth entry points
    │   │   ├── model/               Entities + enums (Role, CampaignStatus, AdType,
    │   │   │                        Audience, NotificationType, TicketStatus, …)
    │   │   ├── dto/request/         Validated request objects
    │   │   ├── dto/response/        Response records returned as JSON
    │   │   ├── repository/          JdbcClient repositories (no JPA)
    │   │   ├── service/             Business logic incl. OptimizationEngine,
    │   │   │                        TrafficModel, SimulationService, ReportService
    │   │   ├── controller/web/      Thymeleaf page controller
    │   │   ├── controller/api/      REST controllers (auth, advertiser, admin)
    │   │   ├── validation/          Custom constraints (Luhn, card expiry,
    │   │   │                        field matching, ordered dates, enum codes, keywords)
    │   │   ├── exception/           Typed exceptions + @RestControllerAdvice handler
    │   │   └── util/                SQL and time helpers
    │   └── resources/
    │       ├── application.properties
    │       ├── schema.sql           8 tables, idempotent
    │       ├── templates/           index, admin-login, dashboard, admin, error,
    │       │                        fragments/head
    │       └── static/
    │           ├── css/             global, auth, admin-login, dashboard, admin
    │           └── js/              common, charts, auth, dashboard, admin
    └── test/java/com/adoptimizer/   54 JUnit 5 tests
```

**Layering:** controller → service → repository → database. Controllers never touch SQL; repositories
never contain business rules. Entities stay inside the service layer; controllers return DTO records, so
password hashes and internal fields can never leak into JSON.

---

## 6. How it works

### 6.1 Request flow

Pages are server-rendered by Thymeleaf, and everything dynamic afterwards is fetched as JSON:

```
Browser ── GET /dashboard ─────────► PageController ──► dashboard.html (Thymeleaf)
Browser ── GET /api/advertiser/... ─► API controller ──► Service ──► Repository ──► MySQL
                                                │
                                          DTO record ──► JSON
```

`common.js` holds a small `Api` client that attaches the CSRF token, parses errors, and turns
field-level validation errors into inline messages under the matching input. `charts.js` draws every
chart on a `<canvas>` — line, bar and doughnut — with no charting library.

### 6.2 Authentication and the admin group code

Sign-in is a JSON call to `/api/auth/login` or `/api/auth/admin/login` rather than Spring Security's
default form login. That choice lets the UI show precise inline errors ("Enter a valid email address",
"Invalid group code. Access denied.") instead of a generic redirect.

The flow:

1. The request DTO is validated (Bean Validation).
2. For the admin endpoint, the submitted group code is compared to `app.admin.group-code` using a
   constant-time comparison (case- and space-insensitive).
3. `AuthenticationManager` authenticates the credentials against BCrypt hashes (strength 12).
4. The role is checked against the portal used, then a session is created and the session ID is rotated
   to prevent session fixation.
5. `LoginAttemptService` locks an email for 5 minutes after 5 failed attempts.

`ActiveAccountFilter` checks on every request that the signed-in user is still active, so deactivating an
account in the admin panel logs that person out immediately, with a notice on the login page.

### 6.3 Campaign lifecycle

```
            ┌──────────── advertiser edits content ◄───────────┐
            ▼                                                  │
  (create) ──► PENDING ──approve──► SCHEDULED ──start date──► ACTIVE ──end date──► COMPLETED
                 │                      │                    ▲    │
              reject                    └──────── pause ─────┘    │
                 ▼                                 resume         │
             REJECTED ──edit & resubmit──► PENDING               budget exhausted
```

- **PENDING** — waiting for admin review. If *auto-approve* is on in Settings, new campaigns skip
  straight to approval unless the content filter flags them.
- **SCHEDULED** — approved, start date in the future.
- **ACTIVE** — delivering. The simulator only spends money on active campaigns.
- **PAUSED** — advertiser paused it; budget stays reserved.
- **COMPLETED** — past the end date or the budget is exhausted.
- **REJECTED** — the admin's reason is shown to the advertiser, who can edit and resubmit.

Editing an approved campaign is deliberately careful: changing only the budget or end date applies
immediately, but changing the title, description, audience, ad format, keywords or start date sends the
campaign back to **PENDING** for review. The edit dialog tells the advertiser which rule applies.

### 6.4 Money: the wallet model

There is no stored "balance" column that could drift out of sync. Every figure is derived:

```
balance    = sum(completed payments) − sum(campaign spend)
committed  = sum(budget − spent) for PENDING, SCHEDULED, ACTIVE and PAUSED campaigns
available  = balance − committed
```

A new campaign can only be created if its budget fits inside `available`, so an advertiser can never
commit money they do not have. Deleting or rejecting a campaign releases its unspent budget back into
`available` automatically. Spending is never refunded.

### 6.5 The traffic simulator

Because this is a coursework project with no real ad network, `SimulationService` (a `@Scheduled` task,
default every 20 s) generates delivery for live campaigns. For each active campaign it:

1. Computes the remaining allowance for today (`dailyBudget − spent today`, capped by the remaining total budget).
2. Asks `TrafficModel` for impressions, clicks and conversions for that allowance.
3. Writes a row into `campaign_daily_stats`, increments the campaign counters and its spend.
4. Moves campaigns to COMPLETED when the end date passes or the budget runs out, and sends a budget
   alert notification at 80% usage.

`TrafficModel` is deliberately tied to the optimizer's advice: the expected CTR depends on the audience
benchmark, the ad format, how many keywords the campaign uses and whether peak-hour delivery is on. That
means applying a suggestion genuinely improves the numbers the campaign produces afterwards. Spend is
priced from the CPC and CPM rates in Settings and never exceeds the allowance.

The simulator can be switched off in **Settings → Traffic simulator**, and **Run simulation now**
delivers one round immediately (useful for demos).

### 6.6 The optimizer

`OptimizationEngine` is pure logic (no database, no Spring), which makes it easy to unit test. For a
campaign it produces a **score out of 100**:

| Component | Weight | Basis |
|---|---|---|
| Click-through rate | 40 | CTR against the audience benchmark |
| Conversion rate | 30 | Conversions per click against the benchmark |
| Budget pacing | 20 | Whether the daily budget covers the remaining days evenly |
| Setup quality | 10 | Keyword count, ad format fit, peak-hour scheduling |

Scores map to *Excellent* (80+), *Good* (65+), *Fair* (45+) and *Needs attention*. Below 500 impressions
the engine reports "not enough data" and uses neutral values instead of punishing a young campaign.

Suggestions are sorted by impact (high → medium → low). Four of them can be applied with one click:

- **Expand your keywords** — adds audience-relevant keywords the campaign is missing.
- **Switch ad format** — moves to the format that performs best for that audience.
- **Schedule ads for peak hours** — restricts delivery to the platform peak window.
- **Pace your budget** — sets the daily budget so the remaining budget lasts to the end date.

Conversion advice is shown as guidance only, because it concerns the landing page rather than anything
the platform controls. Every applied change is written to `optimization_logs` and shown as history on the
Optimizer page and in the campaign detail view; the same suggestion cannot be applied twice.

### 6.7 Content moderation

When a campaign is created or edited, `ModerationService` scans its title, description and keywords for
the blocked words configured in Settings. A match flags the campaign for manual review — it is never
auto-approved, and the admin sees the reason in **Ad Management** and **Moderation**. An admin can clear
a flag, reject the campaign, remove it, or re-scan every campaign after editing the blocked-word list.

### 6.8 Notifications

Advertisers are notified when a campaign is submitted, approved, rejected or removed, when a payment
succeeds, when a campaign reaches 80% of its budget or completes, and when support replies to a ticket.
The sidebar badge and the top-bar dot poll the unread count every 30 seconds.

### 6.9 Reports

`ReportService` builds CSV files in memory and streams them with a
`Content-Disposition: attachment` header and a dated filename. Every cell is quoted and escaped, and
values starting with `=`, `+`, `-` or `@` are prefixed with an apostrophe so a spreadsheet cannot execute
them as formulas (CSV injection protection).

---

## 7. Database schema

Eight tables, created by `schema.sql`:

| Table | Purpose |
|---|---|
| `users` | Advertisers and admins: name, email, company, BCrypt hash, role, active flag, timestamps |
| `campaigns` | Title, description, audience, ad type, budget, daily budget, spend, dates, status, keywords, flags, counters |
| `campaign_daily_stats` | One row per campaign per day: impressions, clicks, conversions, spend |
| `payments` | Reference, amount, method (brand + last 4), status, timestamp |
| `notifications` | Type, title, message, read flag, timestamp |
| `support_tickets` | Reference, subject, message, priority, status, admin reply |
| `optimization_logs` | Which optimization was applied to which campaign, when, and its description |
| `system_settings` | Single-row table holding the runtime platform settings |

Foreign keys cascade from `users` to their campaigns, payments, notifications and tickets, and from
`campaigns` to their statistics and optimization logs.

---

## 8. Validation rules

Every input is validated **twice** — in the browser for instant feedback, and on the server with Bean
Validation, which is the authority. Server-side field errors come back as
`{"message": "...", "fieldErrors": {"budget": "..."}}` and are rendered under the matching input.

Highlights:

- **Name** 2–60 characters, letters/spaces/apostrophes/periods/hyphens only.
- **Email** valid format with a real domain part; must be unique.
- **Password** 6–72 characters with at least one letter and one digit; confirmation must match.
- **Campaign** title 3–100, description 10–1000, valid audience and ad format, budget ≥ the configured
  minimum and within available funds, daily budget ≥ $5 and ≤ total, end date after start date, start
  date not in the past, ≤ 20 keywords of 2–30 characters each.
- **Payment** cardholder name, card number passing the **Luhn check**, expiry `MM/YY` that has not passed
  and is under 20 years away, 3–4 digit CVV, amount $10–$50,000 with at most 2 decimals.
- **Settings** CPC $0.01–$100, CPM $0.01–$1,000, fee 1–30%, minimum budget $10–$100,000, peak hours
  0–23 with end after start.

Custom constraints written for this project: `@LuhnCardNumber`, `@CardExpiry`, `@FieldsMatch`,
`@OrderedFields`, `@EnumCode` and `@KeywordList`.

---

## 9. Security notes

- Passwords hashed with **BCrypt (strength 12)**; never returned by any endpoint.
- **CSRF protection is enabled.** The token is rendered into a `<meta>` tag and sent by the JS client as
  a header on every state-changing request.
- **Role separation** at the URL level: `/api/advertiser/**` requires `ROLE_ADVERTISER`,
  `/api/admin/**` and `/admin` require `ROLE_ADMIN`. Unauthenticated API calls get JSON `401`, wrong-role
  calls get JSON `403`.
- **Ownership checks** on every advertiser resource — a campaign, payment or ticket id belonging to
  someone else returns `404`, not that person's data.
- **Session fixation protection** (session ID rotated on login) and HTTP-only, `SameSite=Lax` cookies.
- **Login throttling**: 5 failed attempts lock an email for 5 minutes.
- **Card data is never stored** — only the brand and last four digits (e.g. `Visa ****4242`).
- **XSS**: all dynamic values are escaped before being inserted into the DOM; Thymeleaf escapes
  server-rendered values.
- **SQL injection**: every query uses parameter binding through `JdbcClient`; sort/filter inputs are
  mapped to a fixed allow-list.
- Errors return generic messages; stack traces are never sent to the browser.

> This is a coursework application. For production you would additionally serve it over HTTPS, move the
> admin group code and database credentials into a secrets manager, and replace the payment simulation
> with a real gateway.

---

## 10. API reference

All API responses are JSON. `✳` marks endpoints that require the CSRF header (all non-GET requests).

### Authentication — `/api/auth`

| Method | Path | Purpose |
|---|---|---|
| POST ✳ | `/login` | Advertiser sign-in |
| POST ✳ | `/register` | Create an advertiser account |
| POST ✳ | `/admin/login` | Admin sign-in (requires group code) |
| POST ✳ | `/admin/register` | Create an admin account (requires group code) |
| POST ✳ | `/logout` | Sign out (Spring Security) |

### Advertiser — `/api/advertiser` (role `ADVERTISER`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/overview?days=7\|30\|90` | KPIs, trend, status counts, recent campaigns |
| GET | `/budget` | Wallet, totals, monthly spend, per-campaign budgets |
| GET | `/wallet` | Balance, committed, available |
| GET | `/campaigns?status=…` | List campaigns |
| POST ✳ | `/campaigns` | Create a campaign |
| GET | `/campaigns/options` | Audiences, ad formats, minimum budget, available funds |
| GET | `/campaigns/optimizable` | Campaigns eligible for optimization |
| GET | `/campaigns/{id}` | Detail + 30-day trend + optimization history |
| PUT ✳ | `/campaigns/{id}` | Update a campaign |
| DELETE ✳ | `/campaigns/{id}` | Delete a campaign |
| POST ✳ | `/campaigns/{id}/pause` · `/resume` | Pause or resume |
| GET | `/campaigns/{id}/optimization` | Score, benchmarks, suggestions, history |
| POST ✳ | `/campaigns/{id}/optimization` | Apply a suggestion (`{"type":"keywords"}`) |
| GET / POST ✳ | `/payments` | Transaction history / add funds |
| GET | `/notifications`, `/notifications/unread-count` | Notifications and unread count |
| POST ✳ | `/notifications/{id}/read`, `/notifications/read-all` | Mark read |
| GET / PUT ✳ | `/profile` | Read / update profile |
| PUT ✳ | `/profile/password` | Change password |
| GET / POST ✳ | `/tickets` | List / open support tickets |

### Admin — `/api/admin` (role `ADMIN`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/dashboard` | Platform metrics, charts, pending queue, open tickets |
| GET | `/analytics` | Impressions, clicks, CTR, conversions, audiences, payments vs spend |
| GET | `/revenue` | Payments, platform earnings, monthly breakdown |
| GET | `/users?status=&q=` | Search and filter advertisers |
| GET | `/users/{id}` | Full account view |
| PATCH ✳ | `/users/{id}/status` | Activate / deactivate |
| GET | `/campaigns?status=…` | All campaigns with owner details |
| POST ✳ | `/campaigns/{id}/approve` · `/reject` · `/clear-flag` | Review actions |
| DELETE ✳ | `/campaigns/{id}` | Remove a campaign |
| GET | `/moderation` | Flagged campaigns, blocked words, counters |
| POST ✳ | `/moderation/scan` | Re-scan every campaign |
| GET | `/tickets?status=…` | Support tickets |
| POST ✳ | `/tickets/{id}/resolve` · `/reopen` | Reply/resolve or reopen |
| GET / PUT ✳ | `/settings` | Read / update platform settings |
| POST ✳ | `/simulation/run` | Run one simulation round now |
| GET | `/reports/summary` | Quick counts |
| GET | `/reports/{type}` | CSV download — `performance`, `revenue`, `users`, `campaigns`, `audience`, `optimization`, `full` |

---

## 11. Guided demo walkthrough

A five-minute tour that exercises the whole system — useful for a presentation or viva.

1. **Sign in as the advertiser** at `/` with `demo@adpro.com` / `demo123`. The Overview shows seeded
   traffic; leave the tab open for a minute and the numbers grow as the simulator runs.
2. **Create a campaign**: *New Campaign* → fill it in → try a budget larger than your available funds and
   watch the inline error → lower it and submit. It lands in **Pending**.
3. **Sign in as the admin** in a second browser (or a private window) at `/admin/login` with
   `admin@adpro.com` / `admin123` and group code `group 5`.
4. **Approve it** in *Ad Management*. Try rejecting another campaign with a reason.
5. **Back on the advertiser tab** → *Notifications* shows the approval; the rejected campaign displays the
   admin's reason and an *Edit* button that resubmits it.
6. **Optimize**: open *Optimizer*, pick "Autumn Collection Launch", read the score and suggestions, apply
   one — the score rises and the change is logged in history.
7. **Add funds** in *Payments* (test card `4242 4242 4242 4242`, any future expiry, any CVV) and watch
   *Budget* update.
8. **Open a support ticket**, then reply to it from the admin *Complaints* page — the reply appears on the
   advertiser's ticket.
9. **Admin extras**: flip *auto-approve* in Settings and create another campaign to see it go live
   instantly; download a CSV from *Reports*; deactivate the advertiser in *Users* and watch their tab get
   signed out on its next action.

---

## 12. Tests

54 JUnit 5 tests covering the pure logic and the validation rules:

```bash
./mvnw test
```

| Test class | Covers |
|---|---|
| `ValidationRulesTest` | Luhn check, card expiry, keyword parsing, and every request DTO's constraint set through a real Hibernate Validator |
| `OptimizationEngineTest` | Scoring bands, which suggestions appear for good/weak setups, prioritisation, pacing arithmetic, insufficient-data handling |
| `TrafficModelTest` | Spend never exceeds the allowance, clicks ≤ impressions, conversions ≤ clicks (25 randomised repetitions), CTR factors |
| `ServiceHelpersTest` | CSV escaping and formula-injection guard, card-brand detection, blocked-word normalisation, content filter |

Beyond these, the application was manually exercised end to end in a real browser: both sign-in pages,
all validation paths, the full campaign lifecycle across both roles, payments, notifications, tickets,
reports, settings and mobile layouts.

---

## 13. Troubleshooting

| Symptom | Fix |
|---|---|
| `Access denied for user 'root'@'localhost'` | Pass your credentials: `DB_USERNAME=… DB_PASSWORD=… ./mvnw spring-boot:run` |
| `Communications link failure` | MySQL is not running, or is on another port — set `DB_URL` |
| `Unknown database 'ad_optimizer'` | Keep `createDatabaseIfNotExist=true` in the URL, or create the schema manually: `CREATE DATABASE ad_optimizer;` |
| Port 8080 already in use | `server.port=8081` in `application.properties`, or `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` |
| `release version 17 not supported` | Install JDK 17 or newer |
| Admin sign-in says "Invalid group code" | The code is `group 5` unless you set `ADMIN_GROUP_CODE` |
| "Too many failed attempts" | Wait 5 minutes, or lower `app.security.lockout-minutes` |
| Numbers never change | The traffic simulator is off in Settings, or no campaign is currently **Active** |
| Want a clean database | `DROP DATABASE ad_optimizer;` then restart — the schema and demo data are recreated |
| `./mvnw: Permission denied` | `chmod +x mvnw` |

---

**Default sign-in:** advertiser `demo@adpro.com` / `demo123` · admin `admin@adpro.com` / `admin123` with
group code `group 5`.


---

## Deployment (GitHub -> Render -> TiDB Cloud)

The app is a Spring Boot server, so it needs a host that runs a JVM (Render, Railway, Fly.io). Netlify only serves static files and short JavaScript functions, so it cannot run this backend.

1. **Database** - create a TiDB Cloud Serverless cluster and a database (e.g. `ad_optimizer`). Copy host, port (4000), user and password.
2. **Render** - New > Blueprint (uses `render.yaml`) or New > Web Service with the repo; runtime is Docker (`Dockerfile`, Java 17).
3. **Environment variables** (set in Render, never commit them):

| Variable | Example |
|---|---|
| `DB_URL` | `jdbc:mysql://<host>:4000/ad_optimizer?sslMode=VERIFY_IDENTITY&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC` |
| `DB_USERNAME` / `DB_PASSWORD` | from TiDB Cloud |
| `ADMIN_GROUP_CODE` | a strong secret (the default is for local demos only) |
| `COOKIE_SECURE` | `true` |
| `THYMELEAF_CACHE` | `true` |
| `SEED_ENABLED` | `false` once you do not want demo data |

4. Every push to `main` runs CI (`.github/workflows/ci.yml`) and Render redeploys automatically.
