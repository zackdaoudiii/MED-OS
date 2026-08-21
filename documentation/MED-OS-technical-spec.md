# DentalOS — Plateforme SaaS de gestion de rendez-vous dentaires avec automatisation WhatsApp

**Spécification fonctionnelle & architecture technique**
Stack : Spring Boot · Spring Security · DDD · Architecture Hexagonale

---

## 1. Vision produit

Un SaaS multi-tenant destiné aux cabinets dentaires (indépendants et petites chaînes) qui :

1. Centralise la gestion des rendez-vous (planning, salles, praticiens).
2. Réduit les no-shows via rappels WhatsApp automatisés (J-3 / J-1 / H-2, configurable).
3. Récupère les créneaux annulés en proposant automatiquement le slot libéré aux patients en liste d'attente.
4. Automatise le suivi post-consultation (contrôle post-soin, rappel détartrage annuel, campagnes de reprise de patients inactifs).

**Modèle de revenu** : abonnement SaaS par cabinet (par praticien actif + volume de messages WhatsApp), avec un tier gratuit limité pour l'acquisition.

---

## 2. Bounded Contexts (DDD)

Le système est découpé en contextes bornés indépendants, chacun avec son propre modèle de domaine, sa propre base de persistance logique (schéma dédié) et son propre cycle de déploiement à terme (modular monolith → microservices si la charge le justifie).

| Bounded Context | Responsabilité | Type |
|---|---|---|
| **Identity & Access** | Comptes cabinet, utilisateurs (praticien, assistant, admin), rôles, tenants | Support |
| **Scheduling** | Agenda, créneaux, disponibilités, salles/chaises, règles métier de prise de RDV | **Core** |
| **Patient Directory** | Fiche patient, historique, consentement RGPD/loi 09-08, préférences de contact | Core |
| **Notification & Messaging** | Orchestration des rappels, templates WhatsApp, statuts de livraison | **Core** |
| **Waitlist & Recovery** | File d'attente, matching automatique créneau libéré ↔ patient éligible | Core |
| **Billing & Subscription** | Plans, facturation, quotas de messages | Support |
| **Analytics & Reporting** | Taux de no-show, ROI campagnes, tableaux de bord cabinet | Generic |

Chaque contexte communique via des **événements de domaine** (event-driven, via un outbox + broker) plutôt que par appel synchrone direct, sauf pour les lectures simples (via des ACL / ports read-only).

### Context Map (relations)

```
Scheduling ──(RendezVousAnnule)──▶ Waitlist & Recovery
Scheduling ──(RendezVousCree/Modifie)──▶ Notification & Messaging
Patient Directory ──(ConsentementRetire)──▶ Notification & Messaging (ACL, Conformist)
Notification & Messaging ──(MessageEchoue/Livre)──▶ Analytics & Reporting
Billing & Subscription ──(QuotaAtteint)──▶ Notification & Messaging (Customer/Supplier)
Identity & Access : Shared Kernel léger (TenantId, UserId) consommé par tous les contextes
```

---

## 3. Architecture hexagonale (Ports & Adapters)

Chaque bounded context est packagé en module Maven/Gradle indépendant, structuré ainsi :

```
scheduling/
├── domain/                    # Cœur métier — ZERO dépendance Spring/JPA
│   ├── model/
│   │   ├── Appointment.java           (Aggregate Root)
│   │   ├── AppointmentId.java         (Value Object)
│   │   ├── TimeSlot.java              (Value Object)
│   │   ├── AppointmentStatus.java     (Enum: PLANNED, CONFIRMED, CANCELLED, NO_SHOW, DONE)
│   │   └── Practitioner.java
│   ├── event/
│   │   ├── AppointmentCancelled.java
│   │   └── AppointmentConfirmed.java
│   ├── exception/
│   │   └── SlotAlreadyBookedException.java
│   └── service/
│       └── SlotAvailabilityPolicy.java   (Domain Service — règles métier pures)
│
├── application/                # Cas d'usage — orchestration, transactions
│   ├── port/
│   │   ├── in/                        # Ports d'entrée (use cases)
│   │   │   ├── BookAppointmentUseCase.java
│   │   │   └── CancelAppointmentUseCase.java
│   │   └── out/                       # Ports de sortie (dépendances externes)
│   │       ├── AppointmentRepository.java
│   │       ├── EventPublisher.java
│   │       └── ClinicRoomGateway.java
│   └── service/
│       └── BookAppointmentService.java  (implémente le use case, dépend uniquement des ports)
│
└── infrastructure/              # Adapters — détails techniques
    ├── in/
    │   ├── web/
    │   │   ├── AppointmentController.java   (REST, DTO, mapping)
    │   │   └── dto/
    │   └── messaging/
    │       └── WhatsAppWebhookListener.java  (réponses patients type "1" pour confirmer)
    └── out/
        ├── persistence/
        │   ├── AppointmentJpaEntity.java
        │   ├── AppointmentJpaRepository.java     (Spring Data)
        │   └── AppointmentRepositoryAdapter.java (implémente le port out)
        ├── event/
        │   └── KafkaEventPublisherAdapter.java
        └── external/
            └── WhatsAppBusinessApiAdapter.java
```

**Règle de dépendance stricte** : `infrastructure → application → domain`. Le `domain` ne dépend d'aucune annotation Spring, JPA ou framework — testable en pur Java/JUnit sans contexte Spring (tests rapides, isolation totale de la logique métier).

Exemple de port d'entrée / cas d'usage :

```java
// application/port/in/BookAppointmentUseCase.java
public interface BookAppointmentUseCase {
    AppointmentId handle(BookAppointmentCommand command);
}

// application/service/BookAppointmentService.java
@UseCase // stéréotype custom, pas de @Service Spring dans ce package idéalement
public class BookAppointmentService implements BookAppointmentUseCase {

    private final AppointmentRepository appointmentRepository; // port out
    private final SlotAvailabilityPolicy availabilityPolicy;   // domain service
    private final EventPublisher eventPublisher;               // port out

    @Override
    public AppointmentId handle(BookAppointmentCommand cmd) {
        var slot = TimeSlot.of(cmd.start(), cmd.end());

        if (!availabilityPolicy.isAvailable(cmd.practitionerId(), slot, appointmentRepository)) {
            throw new SlotAlreadyBookedException(cmd.practitionerId(), slot);
        }

        var appointment = Appointment.schedule(cmd.patientId(), cmd.practitionerId(), slot);
        appointmentRepository.save(appointment);
        eventPublisher.publish(new AppointmentConfirmed(appointment.getId(), cmd.tenantId()));

        return appointment.getId();
    }
}
```

L'adapter JPA (infrastructure) reste totalement remplaçable — on peut swapper PostgreSQL pour un autre store, ou brancher un mock en test, sans toucher au domaine ni à l'application.

---

## 4. Stack technique

| Couche | Choix | Justification |
|---|---|---|
| Langage / Framework | Java 21, Spring Boot 3.x | LTS, virtual threads pour I/O intensif (appels WhatsApp API) |
| Sécurité | Spring Security + OAuth2 Resource Server + Keycloak | Multi-tenant IAM, SSO cabinet, tu maîtrises déjà cette stack |
| Persistance | PostgreSQL (schema-per-tenant ou discriminator selon échelle) | Row-Level Security possible pour isolation tenant supplémentaire |
| Messaging interne | Apache Kafka (Outbox Pattern) | Découplage des bounded contexts, fiabilité de livraison des événements |
| Intégration WhatsApp | WhatsApp Business Platform (Cloud API, Meta) via webhook + API sortante | Templates pré-approuvés Meta pour rappels transactionnels |
| Cache / Rate limiting | Redis | Cache disponibilités, throttling API WhatsApp (limites Meta par tenant) |
| Conteneurisation | Docker + docker-compose (dev), ECS/EKS (prod AWS) | Cohérent avec ton stack AWS existant |
| Observabilité | Micrometer + Prometheus + Grafana, OpenTelemetry tracing | Suivi des taux de livraison message = KPI produit critique |
| Documentation API | springdoc-openapi | Contrat REST versionné |
| Tests | JUnit 5, Testcontainers (Postgres, Kafka), ArchUnit | ArchUnit pour **faire respecter les règles hexagonales dans la CI** |

### ArchUnit — garde-fou architectural (recommandé fortement)

```java
@AnalyzeClasses(packages = "com.dentalos.scheduling")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule layered_architecture = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..application..")
        .layer("Infrastructure").definedBy("..infrastructure..")
        .whereLayer("Domain").mayNotAccessAnyLayer()
        .whereLayer("Application").mayOnlyAccessLayers("Domain")
        .whereLayer("Infrastructure").mayOnlyAccessLayers("Application", "Domain");
}
```

Ce test casse la build dès qu'un dev importe `@Entity` dans le domaine — ça t'évite l'érosion architecturale typique après 6 mois de sprints sous pression.

---

## 5. Sécurité (multi-tenant SaaS)

### 5.1 Modèle d'identité

- **Keycloak** en IdP, un **royaume (realm) par environnement**, tenants isolés via un **claim custom `tenant_id`** dans le JWT (mapper Keycloak).
- Rôles applicatifs : `ROLE_CABINET_ADMIN`, `ROLE_PRACTITIONER`, `ROLE_ASSISTANT`, `ROLE_PLATFORM_ADMIN` (staff DentalOS).
- Chaque requête entrante passe par un `TenantContextFilter` qui extrait `tenant_id` du JWT et le place dans un `ThreadLocal`/contexte de requête (propagé aux threads virtuels via `ScopedValue` si Java 21).

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/webhooks/whatsapp/**").permitAll() // signature-verified séparément
            .requestMatchers("/api/v1/**").authenticated()
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(tenantAwareAuthenticationConverter())))
        .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)
        .build();
}
```

### 5.2 Isolation des données tenant

Deux niveaux de défense (belt-and-suspenders) :

1. **Applicatif** : toute requête Spring Data passe par un `@Where` / filtre Hibernate injectant `tenant_id = :currentTenant` automatiquement.
2. **Base de données** : PostgreSQL **Row-Level Security** (`CREATE POLICY tenant_isolation ON appointments USING (tenant_id = current_setting('app.tenant_id')::uuid)`) — filet de sécurité si un bug applicatif oublie le filtre.

### 5.3 Sécurité WhatsApp

- Vérification de la signature `X-Hub-Signature-256` sur chaque webhook Meta entrant (HMAC-SHA256 avec l'App Secret).
- Tokens d'accès WhatsApp stockés chiffrés (AWS KMS / Vault), un par tenant si BSP dédié, ou compte technique partagé avec routing par `phone_number_id`.
- Conformité RGPD / loi marocaine 09-08 : consentement explicite patient tracé (opt-in horodaté) avant tout envoi WhatsApp marketing (les messages transactionnels de rappel RDV entrent dans une catégorie moins stricte côté Meta, mais le consentement reste tracé côté produit).

---

## 6. Flux clé : automatisation anti-no-show

```
1. RDV créé (Scheduling) → événement AppointmentConfirmed
2. Notification & Messaging consomme l'événement
   → planifie 3 jobs différés (Kafka delayed / Quartz) : J-3, J-1, H-2
3. À chaque échéance : sélection du template WhatsApp selon la langue patient
   → appel WhatsAppBusinessApiAdapter.send(template, variables)
4. Webhook retour Meta : statut delivered / read / failed
   → mise à jour du statut + déclenchement fallback SMS si failed après 2 tentatives
5. Réponse patient "1" (confirmer) ou "2" (annuler) via webhook
   → commande ConfirmAppointmentUseCase ou CancelAppointmentUseCase
6. Si annulation → événement AppointmentCancelled
   → Waitlist & Recovery cherche un patient éligible (même praticien, créneau compatible)
   → envoi automatique d'une offre WhatsApp au 1er patient en liste ("Créneau libéré demain 14h, dispo ?")
   → premier "OUI" reçu = créneau réattribué, les autres notifiés que le créneau est pris
```

Ce flux est le cœur de la proposition de valeur (recovery de créneaux) — il justifie à lui seul l'architecture événementielle plutôt qu'un monolithe à appels synchrones.

---

## 7. Modèle de données (extrait, simplifié)

```sql
-- Scheduling context
CREATE TABLE appointments (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    patient_id      UUID NOT NULL,
    practitioner_id UUID NOT NULL,
    room_id         UUID,
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL,  -- PLANNED, CONFIRMED, CANCELLED, NO_SHOW, DONE
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0  -- optimistic locking
);

-- Outbox pattern pour publication fiable des événements
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    tenant_id       UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);
```

L'**Outbox Pattern** est indispensable ici : on écrit l'état (RDV annulé) et l'événement métier dans la même transaction DB, puis un relais (Debezium CDC ou polling scheduler) publie vers Kafka. Ça évite le classique "j'ai annulé le RDV mais le message Kafka a été perdu → le patient en liste d'attente n'est jamais notifié".

---

## 8. Roadmap technique suggérée (MVP → V1)

| Phase | Contenu |
|---|---|
| **MVP (8-10 sem.)** | Scheduling + Patient Directory + rappels WhatsApp simples (J-1), mono-tenant "logique" (1 seul cabinet pilote), sans Kafka — appels synchrones + `@Async` Spring |
| **V1 (multi-tenant)** | Introduction Keycloak multi-tenant, RLS Postgres, Outbox + Kafka pour découpler Notification, Waitlist & Recovery |
| **V1.1** | Analytics (taux no-show, ROI), Billing/quotas, fallback SMS |
| **V2** | Extraction éventuelle des contextes à forte charge (Notification & Messaging) en service déployable séparément si le monolithe modulaire montre ses limites |

Cette progression **monolithe modulaire → extraction sélective** colle bien à ton contexte : tu peux livrer vite en solo/petite équipe tout en gardant les frontières DDD propres dès le départ, ce qui rend l'extraction future peu coûteuse (les ports sont déjà là).

---

## 9. Points d'attention / risques

- **Limites API WhatsApp Business (Meta)** : quotas de messages/24h par numéro selon la qualité du compte — prévoir un rate limiter par tenant dès le MVP pour ne pas se faire bannir.
- **Templates WhatsApp pré-approuvés** : tout changement de wording de rappel doit repasser par validation Meta (délai 24-48h) — prévoir une bibliothèque de templates versionnée côté produit.
- **Fuseaux horaires** : `TimeSlot` doit être stocké en UTC, converti à l'affichage — piège classique sur les rappels programmés.
- **Consentement/opt-out** : un patient qui répond "STOP" doit être immédiatement exclu de toute campagne, y compris transactionnelle non critique — table `consent` dédiée avec effet immédiat, testée en priorité.

---

*Document généré comme base de discussion technique — à affiner selon la taille réelle du premier cabinet pilote et le budget infra disponible.*