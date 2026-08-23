# Hotel Lepenica — mikroservisi

Seminarski rad iz predmeta Programiranje distribuiranih sistema. Luka Bujošević, 38/2023.

Aplikacija za upravljanje hotelom (sobe, gosti, rezervacije, plaćanja), rađena kao 4
Spring Boot mikroservisa + infrastruktura (Eureka, Config Server, Gateway, RabbitMQ,
Zipkin) i Angular Material frontend.

## Servisi

- `guest-service` (8081) — gosti, CRUD, loyalty poeni
- `room-service` (8082) — sobe, CRUD, status (AVAILABLE / MAINTENANCE)
- `reservation-service` (8083) — rezervacije, Feign pozivi ka guest/room, agregacije, RabbitMQ publisher
- `payment-service` (8084) — plaćanja, RabbitMQ consumer, idempotentno kreiranje

Infrastruktura: `eureka-server` (8761), `config-server` (8888), `api-gateway` (8080),
RabbitMQ (5672, UI 15672), Zipkin (9411), frontend (4200).

Bilo je u zadatku obavezno: Eureka, Gateway, CRUD + validacija + JPA/Postgres, OpenFeign,
Resilience4j i bar jedan agregacioni endpoint, Actuator, Swagger — sve je tu, opisano niže
po servisima. Od opcionih uradio sam sve četiri (Config Server, RabbitMQ, Docker Compose,
JWT na gateway-u), a kao bonus dodao Zipkin tracing, globalni exception handling i
Prometheus metrike.

## Pokretanje

Najlakše preko Dockera:

```bash
docker compose up --build
```

Ovo diže sve — Eureka, Config Server, RabbitMQ, Zipkin, 4 servisa, gateway i frontend.
Prvi build zna da potraje par minuta (Maven skida zavisnosti za svaki servis posebno).

Za demonstraciju load balancing-a:
```bash
docker compose up --build --scale room-service=2
```
U Eureka UI-u se vide dve instance `room-service`, gateway ih rotira preko `lb://`.

### Lokalno bez Dockera

Treba JDK 17, Maven, Node 20+, lokalni Postgres na 5432 i lokalni RabbitMQ na 5672.
Napraviti bazu i nalog `hotel`/`hotel`, i 4 baze: `guestdb`, `roomdb`, `reservationdb`,
`paymentdb`. Redosled pokretanja je bitan — prvo Eureka i Config, pa servisi, pa gateway:

```bash
cd eureka-server      && mvn spring-boot:run   # 8761
cd config-server      && mvn spring-boot:run   # 8888
cd guest-service      && mvn spring-boot:run   # 8081
cd room-service       && mvn spring-boot:run   # 8082
cd reservation-service && mvn spring-boot:run  # 8083
cd payment-service    && mvn spring-boot:run   # 8084
cd api-gateway        && mvn spring-boot:run   # 8080
cd frontend && npm install && npm start        # 4200
```

## Korisni URL-ovi

- Frontend: http://localhost:4200
- Eureka: http://localhost:8761
- Gateway (Swagger agregacija svih servisa): http://localhost:8080/swagger-ui.html
- Swagger po servisu: 8081/8082/8083/8084 + `/swagger-ui.html`
- RabbitMQ UI: http://localhost:15672 (guest/guest)
- Zipkin: http://localhost:9411
- Actuator health: `/actuator/health` na svakom servisu

## Baza

Svaki servis ima svoju bazu (`guestdb`, `roomdb`, `reservationdb`, `paymentdb`), ali u
Dockeru sve žive u jednom Postgres kontejneru — `db-init/init.sql` ih napravi pri prvom
podizanju. Tabele pravi Hibernate (`ddl-auto: update`), a `DataSeeder` ubaci par gostiju
i soba ako je baza prazna. Podaci su u volume-u pa prežive restart; za čist start:

```bash
docker compose down -v
docker compose up --build
```

## JWT

Dva test naloga:

- `manager` / `manager123` — pun pristup
- `receptionist` / `recept123` — bez brisanja i bez izmene inventara soba

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"manager","password":"manager123"}'
```

Frontend token čuva i šalje sam preko interceptora. Javno je prijava, `GET /api/rooms/**`,
Swagger i Actuator; sve ostalo traži token. Uloge (`ROLE_MANAGER` / `ROLE_RECEPTIONIST`)
proverava gateway (`SecurityConfig`) jer je on jedina tačka koja vidi ko poziva — dodavanje,
izmena i brisanje soba, kao i brisanje gosta/rezervacije, dozvoljeno je samo manageru.
Frontend gasi te opcije u UI-ju za recepcionera, ali stvarna zaštita je na gatewayu.

## Demo scenario

1. Login kao manager.
2. Rooms — već ima par soba iz seed-a, može se dodati nova.
3. Guests — dodati gosta.
4. Reservations → New Reservation, izabrati gosta i slobodnu sobu, datume.
   `reservation-service` proveri gosta i sobu preko Feign-a, izračuna cenu (noći × cena),
   odbije preklapanje sa postojećom rezervacijom, i pošalje `ReservationCreatedEvent` na
   RabbitMQ.
5. Payments — automatski se pojavi PENDING plaćanje (napravio ga consumer). Pay by Card →
   COMPLETED.
6. Klik na rezervaciju otvara agregovani prikaz (rezervacija + gost + soba u jednom pozivu).

Za circuit breaker: ugasiti `room-service` (`docker compose stop room-service`) i pokušati
napraviti rezervaciju — Feign pukne, Retry proba tri puta, pa fallback vrati grešku umesto
da padne ceo zahtev. Stanje se vidi na `GET :8083/actuator/circuitbreakers`.

## Struktura

```
hotel-management-microservices/
├── docker-compose.yml
├── config-repo/                # konfiguracija za Config Server
├── eureka-server/
├── config-server/
├── api-gateway/                 # + JWT
├── guest-service/
├── room-service/
├── reservation-service/         # Feign + Resilience4j + RabbitMQ publisher + agregacije
├── payment-service/             # RabbitMQ consumer
├── frontend/                    # Angular Material
└── report/                      # kratak izveštaj (report/IZVESTAJ.md)
```

Svaki backend servis ima svoj `Dockerfile` i `pom.xml`, buildaju se nezavisno.

## Sobe — status vs zauzetost

Dostupnost određene sobe za rezervaciju determiniše se na osnovu dva faktora: 

1. Status sobe - manadžer određuje da li je soba trenutno dostupna (`AVAILABLE`) ili su trenutno u toj sobi neki radovi (`MAINTENANCE`)
2. Zauzetost sobe - određena je datumom željene rezervacije, proverava se da li postoji bilo kakva vrsta preklapanaj sa nekom od već postojećih rezervacija.
```
GET /api/reservations/availability?checkIn=2026-08-01&checkOut=2026-08-05&numberOfGuests=2
GET /api/reservations/timeline?from=2026-08-01&to=2026-08-15   # za gantogram
```

Period je poluotvoren `[checkIn, checkOut)` — boravak koji se završava istog dana kad drugi
počinje se ne računa kao sudar (odjava je ujutru, prijava popodne). Stari podaci sa
`status='OCCUPIED'` se pri startu servisa automatski prebace na `AVAILABLE`
(`RoomStatusMigration`), ne treba ništa ručno raditi.

## Frontend

```
src/app/
  core/        api.service.ts, models.ts, api-error.ts, date.util.ts, auth.*
  shared/      header/, page-header/
  pages/       login/, shell/, guests/, payments/,
               rooms/ (+ room-dialog, room-timeline — gantogram),
               reservations/ (+ reservation-dialog, reservation-details-dialog)
```

Rute: `/home` (gantogram — klik na traku otvara rezervaciju), `/rooms`, `/guests`,
`/reservations`, `/payments`.

Klik na traku u gantogramu otvara `reservation-manage-dialog` gde se na jednom mestu vidi
gost, soba, period, iznos, i mogu se raditi prijava, odjava, otkazivanje, naplata i
refundacija. U dijalogu za novu rezervaciju pored izbora gosta ima i `+` dugme za brzo
dodavanje novog gosta bez izlaska iz forme.

Tabele (Rooms, Guests, Reservations, Payments) imaju svoje filtere i sortiranje preko
`MatTableDataSource` — broj sobe se sortira numerički (9 pre 10, ne leksikografski), a
filter po periodu kod rezervacija radi kao presek intervala, ne poređenje po datumu.

Izmena rezervacije je moguća dok je status PENDING ili CONFIRMED; ponovo se proverava
kapacitet i preklapanje, ali rezervacija ne blokira samu sebe. Ako se izmenom promeni cena,
šalje se `ReservationUpdatedEvent`, a `payment-service` ažurira iznos samo ako plaćanje još
nije naplaćeno — već naplaćen novac se ne prepisuje, to ide kroz refundaciju.

Gantogram se širi kad se dovučeš do ivice (dovuče se još 30 dana), a sa suprotne strane se
skraćuje da upit i DOM ostanu ograničeni (backend odbija prozor duži od godinu dana). Dugme
"Today" vrati te na prvu petinu vidljivog dela; S/M/L menjaju širinu dana.
