# Step 30 — Authentic Multi-Source Intelligence Pack

## Goal

Call Secure Pro must not depend on a single caller/IP database. This pack adds a provider-based authenticated backend that can combine multiple independent sources while keeping all third-party credentials out of the Android APK and GitHub repository.

## Privacy boundary

The backend intentionally does **not** request private subscriber/KYC identity data. Phone lookups are limited to validation, carrier, line type, country/region, formatting, portability and risk metadata that a configured provider is allowed to return. IP lookups are network intelligence only: registration range, ASN/ISP, approximate city/region, reverse DNS, proxy/noise/abuse reputation. IP data must never be presented as a person's exact physical location.

## Phone intelligence sources

1. Device Contacts — local, highest-confidence user-owned identity source.
2. Call Secure local identity cache — local, offline-first.
3. Firebase `caller_directory` — authenticated Call Secure cloud identity directory.
4. Google libphonenumber — offline numbering-plan validation, country and line-type metadata already used by the app.
5. Twilio Lookup v2 — Basic Lookup plus optional paid Line Type Intelligence. Server-side credentials only.
6. Telesign Phone ID — phone type, carrier and registration intelligence. Server-side credentials only.
7. Veriphone v3 — validation, carrier, line type and optional current-carrier/portability intelligence. Server-side credentials only.
8. Abstract Phone Validation — validation, carrier, line type, country and registered-region intelligence. Server-side credentials only.

The backend does not call Twilio Caller Name or Telesign identity add-ons that could expose private subscriber identity.

## IP intelligence sources

1. Existing local IP analyzer — IPv4/IPv6 validity and public/private/reserved scope.
2. RIR RDAP — authoritative internet-resource registration data via official RDAP infrastructure; no API key required.
3. DNS PTR — reverse-DNS hostnames when the network owner has published them; no API key required.
4. IPinfo — network/geolocation/ASN-style enrichment when configured.
5. MaxMind GeoIP — country/city/network/ISP intelligence when configured.
6. IP2Location.io — country/region/city/ASN/proxy intelligence when configured.
7. DB-IP — geolocation, ASN/ISP, connection/proxy/threat metadata when configured.
8. GreyNoise — internet-scanner/business-service intelligence; Community lookup can work without a paid key within provider limits, and full v3 can use a configured key.
9. AbuseIPDB — abuse confidence, ISP/domain and report metadata when configured.

## Backend security model

- Android never contains Twilio/Telesign/Veriphone/Abstract/IPinfo/MaxMind/IP2Location/DB-IP/GreyNoise/AbuseIPDB credentials.
- Both callable functions require Firebase Authentication.
- Provider credentials are stored in one Firebase Secret Manager secret named `CALLSECURE_PROVIDER_CONFIG`.
- Firebase App Check enforcement stays off during initial backend bring-up and should be enabled after Play Integrity is configured and valid traffic is verified.
- The current functions region is `asia-south1`.
- Provider failures are isolated: one source failing does not fail the complete lookup.
- Each provider returns evidence with its source name so UI/logic can retain provenance instead of pretending all data came from one database.

## Provider secret JSON schema

Store the following structure in Firebase Secret Manager. Only include providers you actually configure. Never commit the real values to GitHub.

```json
{
  "twilio": {
    "accountSid": "YOUR_ACCOUNT_SID",
    "authToken": "YOUR_AUTH_TOKEN",
    "lineTypeEnabled": false
  },
  "telesign": {
    "customerId": "YOUR_CUSTOMER_ID",
    "apiKey": "YOUR_API_KEY"
  },
  "veriphone": {
    "apiKey": "YOUR_API_KEY",
    "currentCarrierEnabled": false
  },
  "abstractPhone": {
    "apiKey": "YOUR_API_KEY"
  },
  "ipinfo": {
    "token": "YOUR_TOKEN"
  },
  "maxmind": {
    "accountId": "YOUR_ACCOUNT_ID",
    "licenseKey": "YOUR_LICENSE_KEY"
  },
  "ip2location": {
    "apiKey": "YOUR_API_KEY"
  },
  "dbip": {
    "apiKey": "YOUR_API_KEY"
  },
  "greynoise": {
    "apiKey": "YOUR_API_KEY"
  },
  "abuseipdb": {
    "apiKey": "YOUR_API_KEY"
  }
}
```

An empty `{}` value is valid for initial deployment; key-free RIR RDAP, DNS PTR and GreyNoise Community paths can still be available subject to provider limits.

## Firebase callable functions

- `lookupPhoneIntelligence`
- `lookupIpIntelligence`

Both functions return:

- `evidence`: successful source responses only.
- `providerStatus`: source-by-source status including `OK`, `NOT_CONFIGURED`, `NO_RESULT`, or `ERROR`.
- `configuredSources`: sources that were actually attempted/configured.

The Android `CloudIntelligenceClient` is already prepared to call these functions using the same named Firebase app and authenticated user session as the existing community backend.

## Deployment requirement

Firebase Cloud Functions deployment requires the Firebase project to use the Blaze pay-as-you-go plan. The Android app remains fully usable without Functions because the existing local/Firebase caller identity path is independent.

## Cost controls

- Twilio paid Line Type Intelligence defaults to disabled until explicitly enabled in the secret config.
- Veriphone current-carrier mode defaults to disabled because it consumes more credits than static validation.
- Only enable paid provider options after reviewing the provider's current pricing and coverage.
- Keep provider result caching in the app/backend to reduce repeat paid queries.

## Next activation sequence

1. Upgrade Firebase project to Blaze only when ready to deploy Cloud Functions.
2. Create `CALLSECURE_PROVIDER_CONFIG` in Firebase Secret Manager, initially `{}` or with selected provider credentials.
3. Install Functions dependencies and deploy `lookupPhoneIntelligence` and `lookupIpIntelligence`.
4. Test authenticated calls with key-free sources first.
5. Add provider credentials one source at a time and verify each source status.
6. Wire the prepared Android client into Number Lookup and IP Intelligence UI after backend deployment is confirmed.
7. Configure Play Integrity App Check and then enable callable App Check enforcement.
