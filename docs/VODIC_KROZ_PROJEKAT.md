# Beleške dok sam radio ovaj projekat

Nisam ranije radio Spring Boot, ovo su beleške koje sam pravio dok sam se snalazio, da
ne zaboravim posle šta sam zašto radio. Nije udžbenik, samo podsetnik po delovima.

## Spring Boot uopšte

Umesto ručnog pravljenja HTTP servera, parsiranja JSON-a, konekcije ka bazi — Spring Boot
to sam podesi na osnovu toga šta mu je na classpath-u (auto-konfiguracija). Vidi Postgres
driver → podesi konekciju. Vidi web starter → digne Tomcat. Svaki servis u ovom projektu je
odvojen program, sa svojim `main`, `pom.xml` i portom — to i jeste poenta mikroservisa.

## DI ukratko

Ne praviš objekte sam (`new GuestService(new GuestRepository())`), nego ih Spring napravi i
ubrizga kroz konstruktor:

```java
@Service
public class GuestService {
    private final GuestRepository repository;
    public GuestService(GuestRepository repository) { this.repository = repository; }
}
```

Meni je trebalo malo vremena da mi ovo sedne — u početku sam svuda dodavao `@Autowired` na
polja, dok nisam video da se preporučuje baš konstruktor injection (lakše se testira, polje
može biti `final`).

## Slojevi u servisu (na primeru guest-service)

`controller` (prima HTTP, samo prosleđuje) → `service` (logika) → `repository`
(Spring Data JPA, upiti) → `entity` (mapirano na tabelu). DTO-ovi (`GuestRequest`,
`GuestResponse`) su odvojeni od entiteta da se ne vraća direktno ono što je u bazi i da
validacija ide na ulaz, ne na entitet.

`@RestController` + `@RequestMapping` na klasi, pa `@GetMapping`/`@PostMapping`/... po
metodi. `ResponseEntity` kad treba kontrolisati status kod (npr. 201 na create).

## Baza / JPA

`@Entity` + `@Id` + `@GeneratedValue`. `spring-data-jpa` pravi repository iz interfejsa —
`interface RoomRepository extends JpaRepository<Room, Long>` već ima `findAll`, `save`,
`findById` itd. Metode kao `findByStatus(RoomStatus status)` Spring sam prevede u SQL na
osnovu imena metode, ne treba pisati upit ručno (osim za nešto složenije, tu ide `@Query`).

`ddl-auto: update` znači da Hibernate sam pravi/menja tabele iz entiteta — zgodno za ovaj
projekat, u produkciji bi trebalo migracije (Flyway/Liquibase), ali to nije bilo u zadatku.

Svaki servis ima svoju bazu (`guestdb`, `roomdb`...) — to je namerno, mikroservisi ne bi
smeli da dele bazu, jer onda su opet spregnuti kao monolit samo kroz SQL.

## Validacija i greške

Bean Validation na DTO-u (`@NotBlank`, `@Email`, `@Pattern`...), pa `@Valid` na parametru
kontrolera. Kad padne validacija baca se `MethodArgumentNotValidException` — to hvatam u
`GlobalExceptionHandler` (`@RestControllerAdvice`) i pretvaram u čitljivu poruku umesto
default Spring-ovog JSON-a koji niko ne bi razumeo bez čitanja stack trace-a.

## Eureka i Gateway

Bez Eureke bi servisi morali da znaju tačan host:port jedan drugog, što u Docker-u/cloud-u
nije fiksno. Svaki servis se pri startu registruje u Eureka-i pod svojim imenom
(`spring.application.name`), i onda drugi mogu da ga pozovu preko imena (`lb://room-service`)
umesto IP-ja.

Gateway je jedina ulazna tačka za frontend — ne mora frontend da zna 5 različitih portova,
samo zna gateway, a on rutira dalje po imenu servisa preko Eureke.

## Config Server

Konfiguracija koja je zajednička za više servisa (npr. Eureka URL, Zipkin endpoint) ili bi
se menjala bez redeploy-a, izvučena je u `config-repo/` i servisi je povlače pri startu
(`spring.config.import=optional:configserver:...`). Native profil znači da čita sa fajl
sistema, ne iz git repoa — jednostavnije za lokalno/demo.

## OpenFeign

`reservation-service` treba podatke o gostu i sobi. Umesto ručnog `RestTemplate` poziva sa
URL stringom, Feign klijent je samo interfejs:

```java
@FeignClient(name = "guest-service")
public interface GuestClient {
    @GetMapping("/api/guests/{id}")
    GuestResponse getById(@PathVariable Long id);
}
```

Spring sam napravi implementaciju, i `name` se razrešava preko Eureke (load balanced), ne
treba host/port.

## Resilience4j

Feign poziv može da padne (servis dole, mreža spora...). Bez zaštite bi to srušilo ceo
zahtev ka reservation-service. Na klijent se doda:
- Circuit Breaker — ako previše poziva padne za redom, "otvori" prekidač i ne pokušava
  dalje neko vreme (izbegava dodatno opterećivanje mrtvog servisa),
- Retry — pokuša ponovo par puta pre nego što odustane,
- fallback metoda — šta vratiti ako sve ovo ne uspe (kontrolisana greška umesto 500).

Parametri (failure rate, sliding window, broj pokušaja) su u `application.yml`, ne u kodu —
lakše se menjaju bez rekompajliranja.

## RabbitMQ

Kreiranje rezervacije ne treba da čeka da payment-service napravi plaćanje — to bi ih
suvišno povezalo sinhrono. Umesto toga, `reservation-service` samo pošalje event
(`ReservationCreatedEvent`) na exchange, a `payment-service` ga sluša i sam kreira PENDING
plaćanje kad stigne. Ako payment-service padne na trenutak, poruka čeka u redu — ne gubi se.

Idempotentnost je bitna jer RabbitMQ garantuje "at least once", ne "exactly once" — poruka
može stići dva puta. Zato `reservationId` ima `unique` ograničenje u bazi, pa drugi pokušaj
kreiranja istog plaćanja jednostavno padne na constraint umesto da napravi duplikat.

## JWT na gatewayu

Gateway je OAuth2 Resource Server — validira token, ne izdaje ga (izdavanje je poseban,
prostiji endpoint `/auth/login` koji samo proveri username/password iz konfiguracije i
potpiše token). Downstream servisi ne moraju ništa da znaju o JWT-u, jer gateway je jedina
tačka koja proverava ko poziva.

## Observability

Actuator izlaže `/health`, `/metrics`... — korisno i za sam projekat (npr. Docker healthcheck
gleda `/actuator/health`) i za demonstraciju da servis "živi". Zipkin prati jedan zahtev kroz
sve servise kroz koje prođe (trace id se prenosi kroz header), korisno da se vidi gde tačno
staje ako nešto duže traje.

## Docker Compose

Jedan `docker-compose.yml` diže sve — svaki servis ima svoj `Dockerfile` (multi-stage:
prvo Maven build, pa samo jar u finalnoj slici da ne vuče ceo build alat u produkciju).
Servisi se međusobno vide po imenu iz compose fajla (`room-service`, ne `localhost`), isto
kao i sa Eureka-om ali na nivou mreže.

## Šta bih drugačije

Da sam znao unapred koliko će model zauzetosti soba da se menja, verovatno bih od početka
razdvojio "status sobe" i "zauzetost" umesto što sam prvo imao `OCCUPIED` status pa ga
kasnije uklanjao (v. README). I trebalo mi je predugo da shvatim da Feign timeout treba
podesiti zajedno sa Resilience4j timeout-om, inače jedan od njih puca prvi i drugi nikad ne
stigne da uradi svoj posao.
