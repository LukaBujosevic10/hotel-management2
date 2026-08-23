# 📘 Vodič kroz projekat — Spring Boot mikroservisi od nule

> Namenjeno nekome ko **zna Javu i osnove backend-a**, ali **nikada nije radio Spring Boot**.
> Ovaj dokument je "udžbenik" uz konkretan projekat: ide sloj po sloj, objašnjava
> svaku bitnu anotaciju i — najvažnije — **zašto** se nešto radi baš tako.

Preporučeni način čitanja: otvori ovaj dokument sa jedne strane, a kod projekta sa druge,
pa prati fajlove koje pominjemo.

---

## Sadržaj

1. [Šta je Spring Boot i zašto postoji](#1-šta-je-spring-boot-i-zašto-postoji)
2. [Ključni pojmovi: IoC, DI, bean-ovi, auto-konfiguracija](#2-ključni-pojmovi)
3. [Kako je jedan servis građen — sloj po sloj (guest-service)](#3-anatomija-jednog-servisa)
4. [Baza, JPA i PostgreSQL — kako se objekti čuvaju](#4-baza-jpa-i-postgresql)
5. [Validacija i obrada grešaka](#5-validacija-i-obrada-grešaka)
6. [Od monolita ka mikroservisima — zašto Eureka i Gateway](#6-eureka-i-gateway)
7. [Centralna konfiguracija — Config Server](#7-config-server)
8. [Sinhrona komunikacija — OpenFeign](#8-openfeign)
9. [Otpornost na greške — Resilience4j](#9-resilience4j)
10. [Asinhrona komunikacija — RabbitMQ](#10-rabbitmq)
11. [Bezbednost — JWT na Gateway-u](#11-jwt)
12. [Praćenje rada — Actuator, Swagger, Zipkin](#12-observability)
13. [Kako Docker Compose sve spaja](#13-docker-compose)
14. [Put jednog zahteva kroz ceo sistem](#14-put-jednog-zahteva)
15. [Rečnik pojmova](#15-rečnik-pojmova)

---

## 1. Šta je Spring Boot i zašto postoji

Ako si pisao "goli" Java backend, verovatno si ručno pravio HTTP server, ručno parsirao
JSON, ručno otvarao konekcije ka bazi i ručno spajao klase. Spring je **framework** koji
te poslove preuzima; **Spring Boot** je nadgradnja koja Spring čini "instant" upotrebljivim:
sam podešava razumne podrazumevane vrednosti, ugrađuje web server (Tomcat) u sam JAR, i
pušta te da pišeš **samo poslovnu logiku**.

Tri stvari koje Boot donosi, a stalno ćeš ih koristiti:

- **Starter zavisnosti** — umesto da ručno biraš 15 biblioteka za web, dodaš jednu:
  `spring-boot-starter-web`. Ona povuče sve što ide uz nju, u kompatibilnim verzijama.
- **Auto-konfiguracija** — Boot gleda šta ti je na *classpath*-u i sam podešava.
  Vidi PostgreSQL driver u zavisnostima → podesi konekciju ka bazi. Vidi web starter → digne Tomcat.
- **Konvencija umesto konfiguracije** — ako se držiš očekivane strukture, ne moraš ništa
  da "žičiš" ručno.

U ovom projektu svaki servis je **zaseban Spring Boot program** sa sopstvenim `main`
metodom, `pom.xml`-om i portom. To je suština mikroservisa: mnogo malih programa umesto
jednog velikog.

---

## 2. Ključni pojmovi

Ova četiri pojma su temelj — kad ih razumeš, sve ostalo je lakše.

### 2.1 IoC — Inversion of Control (obrtanje kontrole)
U običnom kodu ti praviš objekte kad ti trebaju: `new GuestService(new GuestRepository())`.
U Spring-u je obrnuto: **framework pravi objekte umesto tebe** i drži ih u tzv.
*ApplicationContext*-u (kontejner objekata). Ti samo kažeš "treba mi GuestService", a Spring
ti ga da. Taj kontejnerski objekat zove se **bean**.

### 2.2 DI — Dependency Injection (ubrizgavanje zavisnosti)
Kako `GuestService` dobija svoj `GuestRepository`? Ne pravi ga sam — Spring mu ga
**ubrizga** kroz konstruktor. Pogledaj `guest-service` → `GuestService.java`:

```java
@Service
public class GuestService {
    private final GuestRepository repository;

    // Spring vidi konstruktor i sam prosledi bean GuestRepository-ja
    public GuestService(GuestRepository repository) {
        this.repository = repository;
    }
}
```

Zašto ovako? Zato što klase ne zavise od *konkretnih* instanci nego od Spring-a koji ih
spaja. To olakšava testiranje (možeš ubaciti lažni repozitorijum) i menjanje implementacija.

### 2.3 Anotacije koje prave bean-ove
Spring skenira tvoje pakete i sve što je označeno pretvara u bean-ove:

| Anotacija | Značenje | Primer u projektu |
|---|---|---|
| `@Component` | generički bean | bazna |
| `@Service` | bean sa poslovnom logikom | `GuestService` |
| `@Repository` | bean za pristup bazi | (JPA repozitorijumi implicitno) |
| `@RestController` | bean koji prima HTTP zahteve | `GuestController` |
| `@Configuration` | klasa koja definiše druge bean-ove | `RabbitConfig`, `OpenApiConfig` |
| `@Bean` | metod koji vraća bean | `hotelExchange()` u `RabbitConfig` |

### 2.4 `@SpringBootApplication` — ulazna tačka
Svaki servis ima klasu kao ova (`GuestServiceApplication.java`):

```java
@SpringBootApplication
public class GuestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GuestServiceApplication.class, args);
    }
}
```

`@SpringBootApplication` je zapravo **tri anotacije u jednoj**:
- `@Configuration` — ova klasa može da definiše bean-ove,
- `@EnableAutoConfiguration` — uključi auto-konfiguraciju,
- `@ComponentScan` — skeniraj ovaj paket i podpakete i pokupi sve `@Component/@Service/...`.

Zato je bitno da svi tvoji paketi budu **ispod** paketa u kom je ova klasa
(`com.hotel.guest.*`) — inače ih Spring neće naći.

---

## 3. Anatomija jednog servisa

Najbolje se uči na najjednostavnijem servisu. `guest-service` nema ni Feign ni RabbitMQ —
čist je CRUD, pa je idealan da razumeš **slojevitu arhitekturu** koju svi servisi dele:

```
HTTP zahtev
   │
   ▼
Controller   ← prima zahtev, vraća odgovor (zna za HTTP)
   │
   ▼
Service      ← poslovna logika (ne zna za HTTP ni za bazu direktno)
   │
   ▼
Repository   ← pristup bazi (SQL se generiše automatski)
   │
   ▼
Entity ↔ Baza (PostgreSQL)
```

Zašto slojevi? Da svaki deo ima **jednu odgovornost**. Controller ne zna kako se čuva u
bazi; Service ne zna da li ga zove HTTP ili nešto drugo; Repository ne zna zašto se nešto
čuva. Kad se nešto menja, menjaš samo jedan sloj.

### 3.1 Entity — objekat koji živi u bazi
`entity/Guest.java`:

```java
@Entity                         // "ova klasa je red u tabeli"
@Table(name = "guests")         // ime tabele
public class Guest {
    @Id                                             // primarni ključ
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // baza dodeljuje id
    private Long id;

    @Column(nullable = false, unique = true)        // NOT NULL + UNIQUE
    private String email;
    // ... ostala polja + geteri/seteri
}
```

**Zašto:** JPA (Java Persistence API) mapira Java objekat na red u tabeli — to se zove ORM
(Object-Relational Mapping). Ti radiš sa objektima, a JPA prevodi u SQL. `@Column(unique=true)`
znači da baza neće dozvoliti dva gosta sa istim mejlom — pravilo se čuva na najnižem nivou.

### 3.2 Repository — pristup bazi bez pisanja SQL-a
`repository/GuestRepository.java`:

```java
public interface GuestRepository extends JpaRepository<Guest, Long> {
    boolean existsByEmail(String email);
    Optional<Guest> findByEmail(String email);
}
```

Ovde je "magija" koja iznenadi svakog ko prvi put vidi Spring Data:
- **Nasleđivanjem** `JpaRepository<Guest, Long>` dobijaš gotove metode: `save`, `findById`,
  `findAll`, `deleteById`, `count`... bez ijedne linije implementacije.
- Metode poput `existsByEmail` **ne pišeš** — Spring iz *imena metode* generiše SQL
  (`SELECT ... WHERE email = ?`). Ovo se zove *derived query*.
- Ti praviš samo `interface`; Spring u pozadini napravi klasu koja ga implementira i
  registruje je kao bean.

**Zašto Optional:** `findByEmail` može da ne nađe ništa. Umesto `null` (koji vodi u
`NullPointerException`), vraća `Optional<Guest>` koji te tera da svesno obradiš "nema ga" slučaj.

### 3.3 DTO — šta ulazi i izlazi preko mreže
Nikad ne izlažemo Entity direktno preko API-ja. Umesto toga koristimo **DTO** (Data Transfer
Object). Imamo dva:
- `dto/GuestRequest.java` — šta klijent **šalje** (bez `id`, sa validacijom),
- `dto/GuestResponse.java` — šta klijent **dobija** nazad.

**Zašto:** (1) sakrivaš interna polja baze, (2) možeš da validiraš ulaz nezavisno od entiteta,
(3) ako promeniš bazu, ne moraš da menjaš ugovor prema klijentu. `GuestResponse.from(guest)`
je statička metoda koja Entity pretvara u DTO.

### 3.4 Service — poslovna logika
`service/GuestService.java` sadrži *pravila*: npr. ne dozvoli dva gosta sa istim mejlom,
dodeli welcome poene pri kreiranju:

```java
public Guest create(GuestRequest req) {
    if (repository.existsByEmail(req.getEmail())) {
        throw new DuplicateResourceException("Guest with email ... already exists");
    }
    Guest g = new Guest();
    apply(g, req);
    g.setLoyaltyPoints(welcomePoints);   // vrednost dolazi iz konfiguracije (vidi ↓)
    return repository.save(g);
}
```

Obrati pažnju na `@Value("${hotel.guest.loyalty.welcome-points:0}")` na vrhu klase — to
znači "uzmi vrednost iz konfiguracije; ako je nema, koristi 0". Ta vrednost stiže iz
**Config Server-a** (poglavlje 7).

### 3.5 Controller — vrata ka svetu
`controller/GuestController.java`:

```java
@RestController
@RequestMapping("/api/guests")     // svi endpointi počinju sa /api/guests
public class GuestController {

    @GetMapping                    // GET /api/guests
    public List<GuestResponse> getAll() { ... }

    @GetMapping("/{id}")           // GET /api/guests/5
    public GuestResponse getById(@PathVariable Long id) { ... }

    @PostMapping                   // POST /api/guests
    public ResponseEntity<GuestResponse> create(@Valid @RequestBody GuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GuestResponse.from(service.create(request)));
    }
}
```

Šta koja anotacija radi:
- `@RestController` = kontroler čiji povratni objekti se automatski pretvaraju u **JSON**
  (Spring koristi Jackson biblioteku za to).
- `@RequestMapping("/api/guests")` = zajednički prefiks putanje.
- `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping` = koja HTTP metoda.
- `@PathVariable Long id` = uzmi `id` iz putanje (`/api/guests/{id}`).
- `@RequestBody GuestRequest request` = telo zahteva (JSON) pretvori u Java objekat.
- `@Valid` = pre nego što uđeš u metodu, **proveri validacije** na DTO-u (poglavlje 5).
- `ResponseEntity` = kad hoćeš da kontrolišeš i HTTP status kod (npr. `201 Created`).

To je ceo životni ciklus: **JSON ulazi → DTO → Service → Entity → baza → Entity → DTO → JSON izlazi.**

---

## 4. Baza, JPA i PostgreSQL

### 4.1 PostgreSQL — baza po servisu
U `application.yml` svakog servisa:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:guestdb}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USER:hotel}
    password: ${DB_PASSWORD:hotel}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

- `jdbc:postgresql://.../guestdb` = servis se povezuje na **PostgreSQL** bazu `guestdb`.
  Za razliku od in-memory baze, ovde podaci **trajno ostaju** (perzistencija) i preživljavaju
  restart servisa/kontejnera.
- `${DB_HOST:localhost}` = "uzmi iz okruženja `DB_HOST`, a ako ga nema, koristi `localhost`".
  Zato lokalno gađa `localhost`, a u Docker-u Compose prosledi `DB_HOST=postgres`
  (ime Postgres kontejnera). Ista slika radi u oba slučaja.
- `ddl-auto: update` = Hibernate (JPA implementacija) **sam pravi/ažurira tabele** iz tvojih
  `@Entity` klasa pri startu. Ne pišeš CREATE TABLE ručno. (U pravoj produkciji ovo se često
  zamenjuje migracionim alatom kao Flyway/Liquibase, ali za projekat je `update` sasvim ok.)
- `dialect: PostgreSQLDialect` = Hibernate-u kaže da generiše SQL baš za PostgreSQL
  (u novijem Hibernate-u se često i sam prepozna, ali eksplicitno je jasnije).

**Baza po servisu (bitno pravilo mikroservisa):** svaki servis ima **svoju** bazu
(`guestdb`, `roomdb`, `reservationdb`, `paymentdb`) i **ne dira** tuđu. Servisi razmenjuju
podatke **samo preko API-ja**, nikad kroz zajedničku tabelu. Zato `reservation-service` ne
čita sobu iz baze direktno, nego zove `room-service` (poglavlje 8). To ih drži nezavisnima:
možeš da menjaš šemu jedne baze bez lomljenja ostalih servisa.

U Docker-u sve 4 baze žive u **jednom** Postgres kontejneru; skripta `db-init/init.sql` ih
kreira pri prvom startu:
```sql
CREATE DATABASE guestdb;
CREATE DATABASE roomdb;
CREATE DATABASE reservationdb;
CREATE DATABASE paymentdb;
```
(Ta skripta se pokreće **samo jednom**, kad je Postgres volume prazan. Za ponovni init:
`docker compose down -v` pa `up --build`.)

### 4.2 Seed podaci
`config/DataSeeder.java` implementira `CommandLineRunner` — to je Spring interfejs čiji se
`run(...)` izvrši **jednom, pri startu** aplikacije. Koristimo ga da ubacimo početne goste/sobe
da demo ne bude prazan.

```java
@Component
public class DataSeeder implements CommandLineRunner {
    public void run(String... args) {
        if (repository.count() > 0) return;   // ne dupliraj ako već ima
        repository.save(...);
    }
}
```

Zato seeder (`if (repository.count() > 0) return;`) sa PostgreSQL-om napuni bazu **samo prvi
put** — pri sledećim restartima podaci već postoje, pa se ne dupliraju.

### 4.3 Pristup bazi (psql)
Pošto je baza sada PostgreSQL, tabele gledaš direktno iz kontejnera:
```bash
docker exec -it postgres psql -U hotel -d guestdb -c "SELECT * FROM guests;"
```
Ili se povežeš bilo kojim alatom (DBeaver, pgAdmin) na `localhost:5432`, korisnik `hotel`,
lozinka `hotel`, baza npr. `guestdb`.

---

## 5. Validacija i obrada grešaka

### 5.1 Bean Validation — pravila na ulazu
Umesto da u kodu pišeš `if (email == null) ...`, staviš anotacije na DTO
(`dto/GuestRequest.java`):

```java
@NotBlank(message = "First name is required")
private String firstName;

@Email(message = "Email must be valid")
private String email;

@Pattern(regexp = "^[+0-9\\-\\s]{6,20}$", message = "Phone must be a valid phone number")
private String phone;
```

Kad kontroler ima `@Valid @RequestBody GuestRequest`, Spring pre ulaska u metodu proverava
sva ova pravila. Ako neko padne, **metoda se ne izvrši** — umesto toga leti izuzetak
`MethodArgumentNotValidException`.

### 5.2 Globalni handler — jedno mesto za sve greške
Da svaki kontroler ne bi hvatao greške ručno, imamo `exception/GlobalExceptionHandler.java`
sa anotacijom `@RestControllerAdvice`. To je "presretač" koji hvata izuzetke iz **svih**
kontrolera i pretvara ih u lep JSON:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(...) {
        return build(HttpStatus.NOT_FOUND, ...);        // 404
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(...) {
        // pokupi sva polja koja su pala validaciju i vrati 400 sa detaljima
    }
}
```

**Zašto:** klijent uvek dobija ujednačen oblik greške (`ApiError`: timestamp, status, poruka,
putanja, lista polja) umesto ružnog stack trace-a. Ovo je jedna od **bonus** stavki u projektu.

Tok: `Service` baci `ResourceNotFoundException` → `@RestControllerAdvice` ga uhvati →
klijent dobije `404` sa jasnom porukom.

---

## 6. Eureka i Gateway

Do sada smo imali jedan servis. Ali kad ih ima više, javljaju se dva problema:

1. **Kako servis A zna gde je servis B?** IP adrese i portovi se menjaju (naročito u
   Docker-u gde se kontejneri dižu/gase). Hardkodovati `http://192.168.0.5:8082` je loše.
2. **Kroz koja vrata klijent (frontend) ulazi?** Ne želimo da frontend zna 4 različita
   porta i da svako ima svoj CORS i svoju bezbednost.

Rešenja: **Eureka** (za problem 1) i **API Gateway** (za problem 2).

### 6.1 Eureka — imenik servisa (service discovery)
`eureka-server` je poseban servis (`@EnableEurekaServer`) koji igra ulogu **telefonskog
imenika**. Svaki drugi servis je **Eureka klijent** i pri startu:
1. **javi se** Eureka-i: "ja sam `room-service`, moja adresa je X, port 8082",
2. periodično šalje **heartbeat** ("živ sam"),
3. povlači **kopiju celog imenika** da zna gde su ostali.

Kad servis padne i prestane sa heartbeat-om, Eureka ga izbaci iz imenika.

U kodu je klijent-strana skoro nevidljiva — dovoljno je imati
`spring-cloud-starter-netflix-eureka-client` u `pom.xml` i ovo u `application.yml`:

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

Otvori `http://localhost:8761` da vidiš spisak registrovanih servisa uživo.

**Zašto ovo menja sve:** sada se servisi zovu **po imenu** (`room-service`), a ne po adresi.
Eureka ime prevede u trenutnu adresu. To je osnova i za Feign i za Gateway.

### 6.2 API Gateway — jedina ulazna tačka
`api-gateway` (Spring Cloud Gateway) stoji ispred svih servisa. Frontend priča **samo** sa
njim (`http://localhost:8080`), a Gateway prosleđuje zahteve dalje. Rute su u
`api-gateway/application.yml`:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: room-service
          uri: lb://room-service          # lb = load balanced, po imenu iz Eureke
          predicates:
            - Path=/api/rooms/**           # sve što ide na /api/rooms/** ...
```

Objašnjenje:
- **predicate** (`Path=/api/rooms/**`) = uslov: "ako putanja odgovara ovome, primeni ovu rutu".
- **uri** `lb://room-service` = "prosledi ka servisu koji se u Eureka-i zove `room-service`".
  Prefiks `lb://` znači **load balancing**: ako postoji više instanci, Gateway ih rotira
  (to je bonus koji demonstriramo sa `docker compose up --scale room-service=2`).

**Zašto Gateway:** jedno mesto za bezbednost (JWT, poglavlje 11), CORS, rutiranje i
agregaciju Swagger-a. Frontend ne mora da zna ništa o unutrašnjoj topologiji.

Bitna razlika u tehnologiji: Gateway je **reaktivan** (gradi se na Spring WebFlux-u, ne na
klasičnom Tomcat/servlet modelu). Zato je i njegova bezbednosna konfiguracija drugačija
(`@EnableWebFluxSecurity` umesto klasične). Poslovni servisi su klasični (servlet) i tu je
sve "obično".

---

## 7. Config Server

Zamisli da imaš PDV stopu (`0.20`) hardkodovanu u `payment-service`. Ako je treba promeniti,
menjaš kod i ponovo build-uješ. A ako istu vrednost koristi više servisa? Nered.

**Config Server** rešava to tako što drži konfiguraciju **na jednom mestu**, a servisi je
povlače pri startu.

- `config-server` (`@EnableConfigServer`) služi fajlove iz foldera `config-repo/`.
- Fajl `config-repo/application.yml` = **deljene** vrednosti za sve servise.
- Fajl `config-repo/payment-service.yml` = vrednosti **samo** za `payment-service`
  (ime fajla = ime servisa).

Primer, u `config-repo/payment-service.yml`:
```yaml
hotel:
  payment:
    tax-rate: 0.20
```

A servis to čita u kodu (`PaymentService.java`):
```java
@Value("${hotel.payment.tax-rate:0.0}")
private BigDecimal taxRate;
```

Kako servis zna da pita Config Server? U svom `application.yml`:
```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```
- `configserver:...` = "povuci konfiguraciju sa ovog Config Server-a pri startu".
- `optional:` = "ako Config Server nije dostupan, nemoj pući, samo nastavi sa lokalnim
  vrednostima". Zgodno za razvoj.

**Zašto ovo:** promeniš vrednost na jednom mestu (u `config-repo`), restartuješ servis i
gotovo — bez rebuild-a koda. Ovo je **opciona tehnologija A** u projektu.

---

## 8. OpenFeign

Sad dolazi srž distribuiranog sistema: `reservation-service` pri kreiranju rezervacije mora
da zna **da li gost postoji** i **koliko soba košta**. Ti podaci žive u drugim servisima.

Bez Feign-a bi ručno pisao HTTP poziv (napravi klijenta, sastavi URL, pošalji GET, parsiraj
JSON, obradi greške). Feign to sakriva: ti napišeš **interfejs**, a Feign napravi
implementaciju koja u pozadini radi HTTP.

`reservation-service/client/RoomClient.java`:
```java
@FeignClient(name = "room-service")          // "room-service" = ime iz Eureke!
public interface RoomClient {

    @GetMapping("/api/rooms/{id}")
    RoomDto getRoomById(@PathVariable("id") Long id);
}
```

Da bi ovo radilo, glavna klasa ima `@EnableFeignClients`.

Šta se dešava kad pozoveš `roomClient.getRoomById(5L)`:
1. Feign vidi `name = "room-service"` → pita **Eureka-u** gde je taj servis.
2. Ako ima više instanci, **LoadBalancer** bira jednu.
3. Feign sastavi `GET http://<adresa>/api/rooms/5`, pošalje, i JSON odgovor pretvori u `RoomDto`.

**Zašto je ovo lepo:** u kodu izgleda kao običan poziv metode, a zapravo je mrežni poziv ka
drugom servisu — i to **bez ijedne hardkodovane adrese**. Menjaš instance, skaliraš, sve radi.

`RoomDto`/`GuestDto` su "ogledala" — kopije samo onih polja koja nam iz drugog servisa trebaju.
Servisi ne dele klase; svaki ima svoju verziju DTO-a. To ih drži nezavisnima.

### Gde se kombinuju podaci (agregacija)
`ReservationService.getDetails(id)` spaja tri izvora u jedan odgovor:
```java
Reservation r = findById(id);          // iz svoje baze
GuestDto guest = getGuestSafe(...);    // Feign -> guest-service
RoomDto room = getRoomSafe(...);       // Feign -> room-service
return new ReservationDetailsResponse(..., guest, room, nights);
```
To je **agregacioni endpoint** (`GET /api/reservations/{id}/details`) — obavezan zahtev
projekta: jedan poziv koji objedini podatke iz više servisa.

---

## 9. Resilience4j

Feign je moćan, ali otvara novi problem: **šta ako `room-service` padne** baš dok
`reservation-service` pokušava da ga pozove? U monolitu poziv metode ne može da "padne na
mreži". U mikroservisima može. Ako to ne obradiš, jedan mrtav servis obori ceo lanac.

**Resilience4j** dodaje tri zaštite oko Feign poziva.

### 9.1 Retry — pokušaj ponovo
Mrežne greške su često prolazne. `@Retry` automatski ponovi poziv nekoliko puta pre nego
što odustane.

### 9.2 Circuit Breaker — "osigurač"
Kao osigurač u struji. Ako servis stalno pada, nema smisla da ga zoveš iznova i iznova
(trošiš vreme, gomilaš greške). Prekidač ima tri stanja:

```
   CLOSED  ──(previše grešaka)──►  OPEN
     ▲                              │
     │                        (prođe vreme)
     │                              ▼
     └──(uspeh)── HALF_OPEN ◄───────┘
              (probni pozivi)
```

- **CLOSED** = normalno, pozivi prolaze. Broji se procenat grešaka.
- **OPEN** = prekidač "iskočio": pozivi se **odmah odbijaju** (ne pokušava ni mrežu),
  odlazi se pravo u fallback. Tako se sistem ne guši.
- **HALF_OPEN** = posle nekog vremena pusti par probnih poziva; ako prođu → CLOSED, ako ne → OPEN.

### 9.3 Fallback — plan B
Kad poziv definitivno ne uspe (ili je prekidač OPEN), poziva se **fallback metoda** koja
vraća bezbednu, unapred pripremljenu vrednost umesto da baci grešku.

U kodu (`ReservationService.java`):
```java
@CircuitBreaker(name = "roomService", fallbackMethod = "roomFallback")
@Retry(name = "roomService")
public RoomDto getRoomSafe(Long roomId) {
    return roomClient.getRoomById(roomId);       // pravi Feign poziv
}

// fallback ima ISTE parametre + Throwable na kraju
public RoomDto roomFallback(Long roomId, Throwable t) {
    log.warn("Fallback za sobu {}: {}", roomId, t.toString());
    return null;                                 // servis nastavlja, ne pada
}
```

Pragovi se ne kodiraju, nego stoje u `application.yml` (tako se lako menjaju):
```yaml
resilience4j:
  circuitbreaker:
    instances:
      roomService:
        sliding-window-size: 10          # gledaj poslednjih 10 poziva
        failure-rate-threshold: 50       # ako 50%+ padne -> OPEN
        wait-duration-in-open-state: 10s # koliko ostane OPEN pre HALF_OPEN
  retry:
    instances:
      roomService:
        max-attempts: 3
        wait-duration: 500ms
```

**Kako da vidiš da radi:** ugasi `room-service` (`docker compose stop room-service`), probaj
da napraviš rezervaciju. Videćeš u logu retry pokušaje, pa fallback. Stanje prekidača:
`GET http://localhost:8083/actuator/circuitbreakers`.

**Zašto ovo:** ovo je razlika između "jedan servis padne i sve stane" i "jedan servis padne,
a sistem degradira dostojanstveno i nastavi da radi".

---

## 10. RabbitMQ

Feign je **sinhrona** komunikacija: pozoveš i **čekaš** odgovor. Ali neke stvari ne moraju
da se dese odmah niti da blokiraju korisnika. Primer: kad se napravi rezervacija, treba
kreirati (za sada prazno, PENDING) plaćanje. Korisnik ne mora da čeka na to.

Za takve slučajeve koristi se **asinhrona** komunikacija preko **message broker-a** (RabbitMQ).
Ideja: `reservation-service` "objavi vest" (event) i **odmah nastavi**; `payment-service`
tu vest pokupi kad stigne i obradi je nezavisno.

### 10.1 Pojmovi RabbitMQ-a
- **Exchange** = pošta koja prima poruke i odlučuje kome ih prosledi.
- **Queue** = poštansko sanduče iz kog konzument čita.
- **Binding** = pravilo koje veže exchange i queue (preko *routing key*-a).
- **Routing key** = "adresa" poruke (`reservation.created`).

Sve troje definišemo u `RabbitConfig.java` (na obe strane):
```java
@Bean public TopicExchange hotelExchange() { return new TopicExchange("hotel.exchange"); }
@Bean public Queue reservationCreatedQueue() { return QueueBuilder.durable("reservation.created.queue").build(); }
@Bean public Binding reservationCreatedBinding() {
    return BindingBuilder.bind(reservationCreatedQueue())
            .to(hotelExchange()).with("reservation.created");
}
```

### 10.2 Publisher (reservation-service)
Posle čuvanja rezervacije, objavi event:
```java
rabbitTemplate.convertAndSend("hotel.exchange", "reservation.created", event);
```
`convertAndSend` uzme Java objekat (`ReservationCreatedEvent`), pretvori ga u JSON
(zbog `Jackson2JsonMessageConverter` bean-a) i pošalje. Servis **ne čeka** — nastavlja dalje.

### 10.3 Consumer (payment-service)
```java
@RabbitListener(queues = "reservation.created.queue")
public void onReservationCreated(ReservationCreatedEvent event) {
    paymentService.createPendingIfAbsent(event.getReservationId(), event.getTotalPrice());
}
```
`@RabbitListener` znači "kad god stigne poruka u ovaj queue, pozovi ovu metodu". JSON se
automatski pretvori nazad u `ReservationCreatedEvent`.

### 10.4 Idempotentnost — zašto je bitna
Broker može, u retkim slučajevima, da **isporuči istu poruku dvaput**. Ako bismo naivno
kreirali plaćanje svaki put, dobili bismo duplikate. Zato:
- polje `reservationId` u tabeli plaćanja je **`unique`**,
- servis prvo proveri `existsByReservationId(...)` i ako već postoji — **ne radi ništa**.

To znači: obradi poruku jednom ili sto puta — rezultat je isti. To je **idempotentnost**,
i ovde je eksplicitno traženo. RabbitMQ je **opciona tehnologija B**.

**Feign vs RabbitMQ — kad koji?**
- Treba ti odgovor odmah (validacija gosta pre rezervacije) → **Feign** (sinhrono).
- Ne treba ti odgovor, i ne želiš da blokiraš (kreiranje plaćanja) → **RabbitMQ** (asinhrono).

---

## 11. JWT

Do sada je svako mogao sve. U pravom sistemu, deo API-ja mora biti zaštićen.
Koristimo **JWT** (JSON Web Token) i stavljamo proveru na **Gateway** — tako da svaki servis
iza njega ne mora sam da brine o bezbednosti.

### 11.1 Šta je JWT
JWT je potpisani token (niz karaktera) koji nosi podatke o korisniku (ko je, koje uloge ima)
i vreme isteka. Ključno: **potpisan je tajnim ključem**, pa se ne može falsifikovati bez
tog ključa. Server ne mora da čuva sesiju — sve piše u tokenu, a server samo proveri potpis.

### 11.2 Kako se dobija (login)
Gateway ima endpoint `/auth/login` (`AuthController.java`) koji za ispravne kredencijale
generiše token (HS256 algoritam, biblioteka Nimbus):
```java
String token = tokenService.generateToken("manager", List.of("ROLE_MANAGER", "ROLE_USER"));
```
Klijent pozove:
```
POST /auth/login  { "username": "manager", "password": "manager123" }
→ { "token": "eyJhbGciOi..." }
```

### 11.3 Kako se koristi i proverava
Za svaki naredni zahtev klijent šalje zaglavlje:
```
Authorization: Bearer eyJhbGciOi...
```
Gateway je podešen kao **OAuth2 Resource Server** (`SecurityConfig.java`) koji:
1. pročita token iz zaglavlja,
2. proveri potpis istim tajnim ključem,
3. ako je validan → pusti zahtev dalje; ako nije → `401 Unauthorized`.

Pravila (ko sme šta) su takođe u `SecurityConfig`:
```java
.pathMatchers("/auth/**").permitAll()                                  // login je javan
.pathMatchers(HttpMethod.GET, "/api/rooms/**").permitAll()             // pregled soba javan
.anyExchange().authenticated()                                         // sve ostalo traži token
```

**Zašto na Gateway-u:** bezbednost je na **jednom mestu**, na ulazu. Servisi iza mogu da budu
"prosti" jer im Gateway propušta samo već proverene zahteve. Ovo je **opciona tehnologija D**.

### 11.4 Kako to frontend radi automatski
U Angular delu, `auth.interceptor.ts` je HTTP presretač koji **svakom** odlaznom zahtevu
zakači `Authorization` zaglavlje sa sačuvanim tokenom — ne moraš ručno u svakom pozivu.

---

## 12. Observability

"Observability" = koliko lako možeš da vidiš **šta se dešava** unutar sistema. Tri alata:

### 12.1 Actuator — zdravlje i metrike
`spring-boot-starter-actuator` dodaje gotove endpointe:
- `/actuator/health` — da li je servis živ (i njegove zavisnosti, npr. baza, broker),
- `/actuator/metrics` — metrike (memorija, broj zahteva...),
- `/actuator/prometheus` — iste metrike u formatu koji Prometheus "kupi" (bonus),
- `/actuator/circuitbreakers` — stanje Resilience4j prekidača.

Šta je izloženo, biraš u `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### 12.2 Swagger / OpenAPI — živa dokumentacija API-ja
`springdoc-openapi` skenira tvoje kontrolere i **sam generiše** interaktivnu dokumentaciju.
Otvori `http://localhost:8081/swagger-ui.html` — vidiš sve endpointe i možeš da ih pozoveš
iz browsera. Anotacije `@Operation`, `@Tag` samo dodaju lepše opise. Na Gateway-u su
Swagger-i svih servisa objedinjeni u jedan (dropdown).

### 12.3 Zipkin — praćenje jednog zahteva kroz sistem (distributed tracing)
Kad zahtev prođe kroz Gateway → reservation → (Feign) guest → (Feign) room, kako da vidiš
ceo put i gde je "zaškripalo"? **Micrometer Tracing + Zipkin** svakom zahtevu dodele
jedinstven **trace ID** koji se prosleđuje kroz sve servise. U Zipkin UI
(`http://localhost:9411`) vidiš ceo lanac kao vremensku traku. Ovo je **bonus** stavka.

---

## 13. Docker Compose

Imamo 8+ procesa (Eureka, Config, 4 servisa, Gateway, RabbitMQ, Zipkin, frontend). Ručno
dizati svaki je naporno. **Docker** svaki servis spakuje u **kontejner** (izolovan mini-OS
sa svime što servisu treba), a **Docker Compose** digne sve kontejnere jednom komandom.

### 13.1 Dockerfile — recept za jedan kontejner
Svaki servis ima `Dockerfile` sa **dve faze**:
```dockerfile
# 1) build faza: Maven build-uje JAR unutar kontejnera
FROM maven:3.9-eclipse-temurin-17 AS build
COPY . .
RUN mvn clean package -DskipTests

# 2) run faza: samo JRE + gotov JAR (mala, čista slika)
FROM eclipse-temurin:17-jre
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```
Zašto dve faze: build alati (Maven) ostaju u prvoj fazi; finalna slika nosi samo ono što
treba za pokretanje → manja i bezbednija.

### 13.2 docker-compose.yml — dirigent
Opisuje **sve** servise, njihove portove, zavisnosti i mrežu:
```yaml
services:
  reservation-service:
    build: ./reservation-service
    environment:
      - EUREKA_URL=http://eureka-server:8761/eureka/   # ime kontejnera = hostname!
      - RABBITMQ_HOST=rabbitmq
    depends_on:
      eureka-server: { condition: service_healthy }
```
Ključne stvari:
- Unutar Compose mreže kontejneri se vide **po imenu** (`eureka-server`, `rabbitmq`) — Docker
  ima interni DNS. Zato u `environment` prosleđujemo `http://eureka-server:8761`, ne `localhost`.
- `depends_on` + `healthcheck` = ne diži servise dok Eureka/RabbitMQ nisu spremni.
- `environment` promenljive **pregaze** podrazumevane vrednosti iz `application.yml`
  (setimo se `${EUREKA_URL:...}` — to je "uzmi iz okruženja, ili default").

Pokretanje svega:
```bash
docker compose up --build
```
Ovo je **opciona tehnologija C**.

---

## 14. Put jednog zahteva

Da povežemo sve u priču — šta se tačno desi kad korisnik napravi rezervaciju:

```
1. Korisnik u Angular-u klikne "Book".
2. Frontend šalje POST /api/reservations na Gateway (:8080),
   sa "Authorization: Bearer <token>" (dodao interceptor).
3. Gateway proveri JWT (validan?) → da → pogleda rutu (Path=/api/reservations/**)
   → prosledi na lb://reservation-service (Eureka mu da adresu).
4. reservation-service:
   a) @Valid proveri ulaz (datumi, broj gostiju...).
   b) Feign -> guest-service: da li gost postoji?   [štiti Resilience4j]
   c) Feign -> room-service:  soba slobodna? koja cena?  [štiti Resilience4j]
   d) izračuna totalPrice = broj_noći * cena.
   e) sačuva rezervaciju u SVOJU bazu (status CONFIRMED).
   f) Provera preklapanja: ako soba vec ima rezervaciju koja se sece sa
      trazenim periodom, vraca se 409 sa objasnjenjem. Soba se NE oznacava
      kao zauzeta - zauzetost je izvedena iz samih rezervacija.
   g) RabbitMQ: objavi ReservationCreatedEvent i ODMAH vrati odgovor korisniku.
5. Nezavisno, malo kasnije:
   payment-service (@RabbitListener) pokupi event ->
   kreira PENDING plaćanje (idempotentno, po reservationId).
6. Frontend osveži i prikaže novu rezervaciju; u tabu Payments se pojavi PENDING plaćanje.

Kroz sve ovo, Zipkin je pratio jedan trace ID; Actuator zna da su servisi zdravi.
```

Primeti kako se prepliću **svi** koncepti: Gateway+JWT (ulaz), Eureka (adrese),
Feign+Resilience4j (sinhrono, otporno), sopstvena baza po servisu, RabbitMQ (asinhrono),
Config (PDV pri plaćanju), Observability (praćenje).

---

## 15. Rečnik pojmova

| Pojam | Kratko objašnjenje |
|---|---|
| **Bean** | Objekat kojim upravlja Spring (pravi ga i ubrizgava umesto tebe) |
| **DI / IoC** | Spring spaja klase umesto tebe (kroz konstruktore) |
| **Starter** | Grupna zavisnost (`spring-boot-starter-web`) koja povuče sve potrebno |
| **Auto-konfiguracija** | Spring sam podesi stvari na osnovu onoga što je na classpath-u |
| **Entity** | Java klasa mapirana na tabelu u bazi (JPA/ORM) |
| **Repository** | Interfejs za pristup bazi; Spring generiše implementaciju |
| **DTO** | Objekat za ulaz/izlaz preko mreže (ne izlažemo Entity direktno) |
| **Bean Validation** | Pravila na poljima (`@NotBlank`, `@Email`) proverena sa `@Valid` |
| **Service discovery (Eureka)** | Imenik servisa; zoveš po imenu, ne po adresi |
| **API Gateway** | Jedina ulazna tačka; rutiranje + bezbednost |
| **`lb://`** | Load-balanced ruta preko Eureke (rotira instance) |
| **Config Server** | Centralno mesto za konfiguraciju |
| **OpenFeign** | Deklarativni HTTP klijent (interfejs umesto ručnog poziva) |
| **Circuit Breaker** | "Osigurač" koji prestane da zove servis koji stalno pada |
| **Fallback** | Rezervni odgovor kad poziv ne uspe |
| **Message broker (RabbitMQ)** | Posrednik za asinhrone poruke između servisa |
| **Idempotentnost** | Ponovljena obrada iste poruke daje isti rezultat (bez duplikata) |
| **JWT** | Potpisani token koji nosi identitet i uloge korisnika |
| **Actuator** | Endpointi za zdravlje i metrike servisa |
| **Distributed tracing (Zipkin)** | Praćenje jednog zahteva kroz sve servise |
| **Docker / Compose** | Pakovanje servisa u kontejnere i dizanje svih odjednom |

---

## Gde dalje

- Otvori `guest-service` prvi (najjednostavniji), pa `reservation-service` (najbogatiji).
- Pokreni sve (`docker compose up --build`), pa prati logove dok kroz frontend praviš
  rezervaciju — videćeš u realnom vremenu kako event putuje do plaćanja.
- Poigraj se: ugasi jedan servis i posmatraj Circuit Breaker; promeni PDV u `config-repo`
  i vidi kako se plaćanje promeni posle restarta.

Srećno! Kad jednom "klikne" slojevita struktura + komunikacija, ostali projekti su varijacije
na istu temu.
