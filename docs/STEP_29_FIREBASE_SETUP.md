# Step 29 — Firebase Cloud Backend Setup

Call Secure Pro now contains a real Firebase Authentication + Cloud Firestore client. Firebase project values are intentionally **not committed to GitHub**. The Android app reads them from Gradle properties at build time.

## 1. Firebase project

Create/select a Firebase project and register the Android app with package:

`com.tridev.callsecurepro`

The app does not require `google-services.json`; it initializes Firebase programmatically from the three values below.

## 2. Enable Authentication

In Firebase Authentication, enable the **Anonymous** sign-in provider.

This identity is installation-scoped and is used only to authenticate community caller-directory reads and immutable community report submissions. It is not treated as a permanent user account.

## 3. Create Cloud Firestore

Create the default Cloud Firestore database in production/locked mode.

Deploy the repository rule file:

`firestore.rules`

The rules allow:

- authenticated exact-document reads from `caller_directory/{numberHash}` only when the record is published and not expired;
- authenticated create-only report submissions under the caller's Firebase UID;
- no client list/export of the caller directory;
- no client write/update/delete of caller-directory aggregate identities;
- no raw phone number in cloud report documents;
- deny-by-default for every other path.

## 4. Local Gradle properties

Put these values in the developer machine's **user Gradle properties** file, not in the repository:

Windows:

`C:\Users\<WindowsUser>\.gradle\gradle.properties`

Properties:

```properties
CALLSECURE_FIREBASE_API_KEY=YOUR_FIREBASE_WEB_API_KEY
CALLSECURE_FIREBASE_APP_ID=YOUR_ANDROID_FIREBASE_APP_ID
CALLSECURE_FIREBASE_PROJECT_ID=YOUR_FIREBASE_PROJECT_ID
CALLSECURE_FIREBASE_APP_CHECK_ENABLED=false
```

The first three values are available in Firebase Project settings. Keep App Check `false` until Play Integrity is registered and tested.

After editing the user Gradle properties, sync/rebuild the Android project.

## 5. App Check — recommended before production

After the Firebase Android app has the required SHA-256 certificate fingerprint and Play Integrity is configured, set:

```properties
CALLSECURE_FIREBASE_APP_CHECK_ENABLED=true
```

Then enable App Check enforcement for Cloud Firestore only after verifying valid production builds receive tokens.

## 6. Caller directory document schema

Collection:

`caller_directory`

Document ID:

`SHA-256(normalizedPhoneNumber)` as 64 lowercase hexadecimal characters.

Required document fields used by the Android client:

- `status`: `PUBLISHED`
- `displayName`: non-empty caller/business name
- `displayNumber`: optional formatted number
- `category`: optional category
- `identityType`: `PERSON`, `BUSINESS`, or `UNKNOWN`
- `verificationLevel`: `VERIFIED` or `UNVERIFIED`
- `source`: trusted publishing source label
- `confidence`: integer from 0 to 100
- `expiresAt`: Firestore Timestamp in the future

Mobile clients cannot publish these records. A trusted administrative/server process must create and moderate directory aggregates.

## 7. Community reports

Cloud path:

`community_report_submissions/{firebaseUid}/reports/{dailyReportHash}`

The report contains only:

- SHA-256 number hash
- controlled category
- server timestamp
- app version
- schema version
- Android client marker

The raw phone number stays in the local Room outbox and is not uploaded by Step 29.

The cloud document ID is deterministic per Firebase UID + number hash + category + UTC day. Together with the app's rolling 24-hour local duplicate guard, this prevents accidental duplicate submissions.

## 8. Authentication and lookup behavior

At app startup, Firebase authentication is warmed up asynchronously when configuration exists.

Unknown-number resolution remains offline-first:

1. device contact
2. valid local identity cache
3. authenticated Firestore exact lookup
4. local unknown/risk assessment

Remote caller lookup is time-bounded. Network/Auth/Firestore failures never invent caller data and never prevent local screening from continuing.

## 9. Deployment

This repository includes `firebase.json` and `firestore.rules` so rules can be deployed with Firebase tooling after selecting the real project.

Do not commit project credentials, service-account keys, or private server credentials to this repository.
