# DentalOS — User Stories Backlog

Titles only, grouped by epic. Add descriptions / acceptance criteria under whichever ones you want, then we validate together.

## Epic: Identity & Access
- US-01 — Cabinet registration & tenant provisioning
  As a prospective cabinet admin, I want to register my cabinet on the platform,
  so that I can start managing appointments without manual setup by DentalOS staff.

    - Acceptance Criteria:
      - Cabinet admin registers via a public web form (cabinet name, admin name, email, phone)
      - System rejects registration if email already associated with an existing tenant
      - System creates a new tenant (tenant_id), a cabinet admin user (ROLE_CABINET_ADMIN),
        and assigns the default free-tier plan
      - Cabinet account is inactive until email verification is completed
      - Confirmation/verification email sent to cabinet admin with activation link

  Technical note: should emit a TenantProvisioned domain event (consumed later by
  Billing for trial plan setup, and Analytics for signup tracking).
- US-03 — SSO login via Keycloak
- US-04 — Cabinet admin manages team members (invite, deactivate, change role)

## Epic: Scheduling
- US-05 — Practitioner defines weekly availability & working hours
- US-06 — Assistant books an appointment for a patient
- US-07 — Patient self-books via public booking link
- US-08 — Practitioner blocks a time slot (vacation, personal, room maintenance)
- US-09 — Reschedule an existing appointment
- US-10 — Cancel an appointment (staff-initiated)
- US-11 — Prevent double-booking on the same practitioner/room
- US-12 — Multi-chair/room assignment for an appointment
- US-13 — Daily/weekly calendar view per practitioner

## Epic: Patient Directory
- US-14 — Create/edit patient profile (contact info, language preference)
- US-15 — Record patient consultation history
- US-16 — Capture WhatsApp marketing consent (opt-in) at intake
- US-17 — Patient requests data deletion / opt-out (RGPD / loi 09-08)
- US-18 — Search/filter patient list

## Epic: Notification & Messaging (WhatsApp)
- US-19 — Configure reminder schedule per cabinet (J-3 / J-1 / H-2)
- US-20 — Send automated appointment reminder via WhatsApp
- US-21 — Patient confirms appointment via WhatsApp reply
- US-22 — Patient cancels appointment via WhatsApp reply
- US-23 — Fallback to SMS after WhatsApp delivery failure
- US-24 — Cabinet customizes reminder message template (within Meta-approved templates)
- US-25 — Track delivery/read status of sent messages
- US-26 — Patient replies "STOP" → immediate opt-out enforcement
- US-27 — Post-consultation follow-up message (e.g., post-care check-in)
- US-28 — Annual recall campaign (e.g., cleaning reminder) for inactive patients

## Epic: Waitlist & Recovery
- US-29 — Patient joins waitlist for a fully booked practitioner/period
- US-30 — Auto-detect a cancelled slot and identify eligible waitlist patients
- US-31 — Send automated slot-offer WhatsApp message to first eligible patient
- US-32 — First "YES" reply claims the slot; others notified it's taken
- US-33 — Cabinet views/manages waitlist manually

## Epic: Billing & Subscription
- US-34 — Cabinet selects/upgrades subscription plan
- US-35 — Track WhatsApp message quota usage per tenant
- US-36 — Notify cabinet approaching/exceeding quota
- US-37 — Generate monthly invoice

## Epic: Analytics & Reporting
- US-38 — Dashboard: no-show rate over time
- US-39 — Dashboard: recovered slots via waitlist (ROI view)
- US-40 — Dashboard: message delivery/read/failure rates
- US-41 — Export report (CSV/PDF) for a given period

## Epic: Frontend — Cabinet Web App (Admin/Assistant/Practitioner)
- US-42 — Login / SSO screen (Keycloak redirect flow)
- US-43 — Onboarding wizard for new cabinet (practitioners, rooms, working hours)
- US-44 — Calendar view (day/week) with drag-and-drop rescheduling
- US-45 — New appointment booking modal/form
- US-46 — Appointment detail panel (edit, cancel, history)
- US-47 — Patient list view with search/filter
- US-48 — Patient profile page (info, consent status, appointment history)
- US-49 — Waitlist management screen
- US-50 — Reminder template configuration screen
- US-51 — WhatsApp message log / delivery status view per patient
- US-52 — Analytics dashboard (no-show rate, recovered slots, message stats)
- US-53 — Billing/subscription screen (plan, quota usage, invoices)
- US-54 — Team management screen (invite/deactivate staff, roles)
- US-55 — Notification/toast system for real-time updates (e.g., slot claimed)
- US-56 — Responsive layout for tablet use at front desk

## Epic: Frontend — Patient-Facing Web
- US-57 — Public self-booking page (select practitioner, date, slot)
- US-58 — Booking confirmation page
- US-59 — Consent/opt-in capture form (WhatsApp marketing)
- US-60 — Waitlist join form (public link)

## Epic: Frontend — Platform Admin (DentalOS staff)
- US-61 — Tenant management console (list cabinets, plan, status)
- US-62 — Global WhatsApp quota/usage monitoring across tenants
- US-63 — Impersonate/support view for a given cabinet (debugging)