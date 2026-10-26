# Android Transfer Servis — staging Debug configuration

Status: source only, no staging host or device test was provisioned as part of this change.

## Existing architecture

The PHP gateway remains responsible for Worker HMAC signing. Never embed the Worker PROXY_HMAC_SECRET or passenger signing secret in Android. Existing API endpoints are unchanged:

- api/calculate.php
- api/order.php
- api/order-status.php

Release base URL remains https://transfer-servis52.ru/. Only Debug is configurable.

## Local setup, after separate staging PHP vhost is approved and deployed

Put these properties **only** in a developer's untracked user Gradle properties file (Windows: %USERPROFILE%\.gradle\gradle.properties). Never commit or send the values to chat:

    transferStagingBaseUrl=https://YOUR-APPROVED-STAGING-HOST/
    transferStagingAccessToken=YOUR-TEMPORARY-STAGING-TESTER-TOKEN

The hosting administrator provides the actual HTTPS vhost URL after deployment; it is deliberately NOT guessed in source code. The token is an independent, revocable, staging-only tester credential. It will be compiled into a Debug APK, therefore do not distribute the APK; rotate/revoke the token when QA ends. Do not use real passenger data.

If transferStagingBaseUrl is not supplied, Debug uses the deliberately non-routable https://staging-not-configured.invalid/ and cannot accidentally create orders in production. The Debug build rejects plain HTTP and the exact production URL. Release does not use the staging token.

## Validate

1. Check that production Android methods, DTOs, repositories, pending order recovery and AES-GCM storage were left intact.
2. Run Gradle unit tests and connected Android instrumentation against local/mocked services first.
3. After owner approval for staging-vhost deployment, configure HTTPS, access control, staging-only PHP secret files and temporary tester token.
4. On Debug, validate calculate -> createOrder -> persisted session -> force-stop/relaunch -> order status. Ensure no actual Telegram or FCM delivery.
5. Test failed replies/manual recovery and idempotency, then rotate staging tester token.

This change is intentionally draft and depends on the staging PHP gateway in public_html. No merge or production release is authorized by this document.
