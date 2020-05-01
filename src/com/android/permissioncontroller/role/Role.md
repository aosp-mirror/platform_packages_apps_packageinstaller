<!--
  Copyright (C) 2020 The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License
  -->

# Android Role for system developers

This document targets system developers. App developers should refer to the [RoleManager
documentation](https://developer.android.com/reference/android/app/role/RoleManager) and AndroidX
[core-role](https://developer.android.com/reference/androidx/core/role/package-summary) library.

## Definition

A role is a unique name within the system for a purpose, associated with certain requirements and
privileges if granted. For example, the SMS role requires the app to have certain declarations in
its manifest that are central to SMS functionality, and grants the app privileges like reading and
writing user's SMS.

The list of available roles and their behavior can be updated via PermissionController upgrade, out
of the platform release cycle. Since Android Q, all the default apps (e.g. default SMS app) are
backed by a corresponding role implementation.

The definition for all the roles can be found in [roles.xml](../../../../../res/xml/roles.xml) and
associated [`RoleBehavior`](model/RoleBehavior.java) classes.

## Defining a role

A role is defined by a `<role>` tag in `roles.xml`.

The following attributes are available for role:

- `name`: The unique name to identify the role, e.g. `android.app.role.SMS`.
- `behavior`: Optional name of a [`RoleBehavior`](model/RoleBehavior.java) class to control certain
role behavior in Java code, e.g. `SmsRoleBehavior`. This can be useful when the XML syntax cannot
express certain behavior specific to the role.
- `defaultHolders`: Optional name of a system config resource that designates the default holders of
the role, e.g. `config_defaultSms`. If the role is not exclusive, multiple package names can be
specified by separating them with semicolon (`;`).
- `description`: The string resource for the description of the role, e.g.
`@string/role_sms_description`, which says "Apps that allow you to use your phone number to send and
receive short text messages, photos, videos, and more". For default apps, this string will appear in
the default app detail page as a footer. This attribute is required if the role is `visible`.
- `exclusive`: Whether the role is exclusive. If a role is exclusive, at most one application is
allowed to be its holder.
- `label`: The string resource for the label of the role, e.g. `@string/role_sms_label`, which says
"Default SMS app". For default apps, this string will appear in the default app detail page as the
title. This attribute is required if the role is `visible`.
- `requestDescription`: The string resource for the description in the request role dialog, e.g.
`@string/role_sms_request_description`, which says "Gets access to contacts, SMS, phone". This
description should describe to the user the privileges that are going to be granted, and should not
be too long. This attribute is required if the role is both `visible` and `requestable`.
- `requestTitle`: The string resource for the title of the request role dialog, e.g.
`@string/role_sms_request_title`, which says "Set %1$s as your default SMS app?". This attribute is
required if the role is both `visible` and `requestable`.
- `requestable`: Whether the role will be requestable by apps. If a role isn't requestable but is
still visible, apps cannot show the request role dialog to user, but user can still manage the role
in Settings page. This attribute is optional and defaults to the value of `visible`.
- `searchKeywords`: Optional string resource for additional search keywords for the role, e.g.
`@string/role_sms_search_keywords` which says "text message, texting, messages, messaging". The role
label is always implicitly included in search keywords.
- `shortLabel`: The string resource for the short label of the role, e.g.
`@string/role_sms_short_label`, which says "SMS app". For default apps, this string will appear in
the default app list page as the title for the default app item. This attribute is required if the
role is `visible`.
- `showNone`: Whether this role will show a "None" option. This allows user to explicitly select
none of the apps for a role. This attribute is optional, only applies to `exclusive` roles and
defaults to `false`.
- `systemOnly`: Whether this role only allows system apps to hold it. This attribute is optional and
defaults to `false.
- `visible`: Whether this role is visible to users. If a role is invisible (a.k.a. hidden) to users,
users won't be able to find it in Settings, and apps won't be able to request it. The role can still
be managed by system APIs and shell command.

The following tags can be specified inside a `<role>` tag:

- `<required-components>`: Child tags like `<activity>`, `<service>`, `<provider>` and `<receiver>`
can be used to specified the app manifest requirements of the role, and an app is only qualified
when it declares all these components. They follow a similar syntax as in typical
`AndroidManifest.xml`.
- `<permissions>`: Child tags like `<permission-set>` and `<permission>` can be used to specify the
permissions that should be granted to the app when it has the role. Several `<permission-set>` are
defined at the beginning of `roles.xml`.
- `<app-op-permissions>`: The child tag `<app-op-permission>` can be used to specify the app op
permissions whose app op should be granted to the app when it has the role.
- `<app-ops>`: The child tag `<app-op>` can be used to specify the app ops that should be granted to
the app when it has the role.
- `<preferred-activities>`: The child tag `<preferred-activity>` can be used to specify the
preferred activities that should be configured for the app when it gets the role. The first
`<activity>` tag inside `<preferred-activity>` will identify the activity component inside the app,
and the other `<intent-filter>` tags inside `<preferred-activity>` can be used to specify for which
intent filters the identified activity component should be configured as preferred, i.e. the default
handler for those intents.

## Requesting a role

Before requesting a role, an app should check whether it already has the role with
`RoleManager.isRoleHeld()`. If it doesn't have the role, it should then check for the availability
of the role with `RoleManager.isRoleAvailable()`.

An app can request for a role by launching the intent returned by
`RoleManager.createRequestRoleIntent()`. If the role is unavailable or the app isn't qualified for
the role, the request role dialog won't show up and will return `RESULT_CANCELED` immediately. If
the role is granted to the app, it will return `RESULT_OK`.

The following is an example about how to request the SMS role:

```kotlin
val roleManager = getSystemService(RoleManager::class.java)
if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
    // We already have the role.
} else if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS), REQUEST_CODE)
    // Check the result later in onActivityResult().
} else {
    // Role is unavailable.
}
```

## Checking a role

Role is not a replacement for permission, and if one needs to check a certain privilege for an
action, they should typically check a permission instead, and introduce a new permission if there
isn't an existing one.

`RoleManager.isRoleHeld()` can be used to check whether an app itself has a role. For checking
whether an arbitrary app has a certain role, `RoleManager.getRoleHoldersAsUser()` can be used to
retrieve the list of role holders and check if the app is within the list. This is a system API and
requires the `MANAGE_ROLE_HOLDERS` permission.

## Managing a role

Generally roles are managed by the role implementation and the user, so it's less likely one should
manage them manually.

In case the system does need to manage the holders of a role, `RoleManager.addRoleHolderAsUser()`,
`RoleManager.removeRoleHolderAsUser()` and `RoleManager.clearRoleHoldersAsUser()` may be used. These
are system APIs and require the `MANAGE_ROLE_HOLDERS` permission. These requests are asynchronous
and the role might not be modified until the `callback` is notified. The role requirements and
behavior will still apply even if managed via these APIs, so the request might fail and one need to
check the result in `callback`. In the event that the role controller hanged or crashed, the
`callback` will return with failure after a certain timeout.

## Shell command

The current list of roles and their holders can be checked with the following shell command on
device:

```bash
dumpsys role
```

You can also manage the role holders with `cmd role`:

```bash
cmd role add-role-holder [--user USER_ID] ROLE PACKAGE [FLAGS]
cmd role remove-role-holder [--user USER_ID] ROLE PACKAGE [FLAGS]
cmd role clear-role-holders [--user USER_ID] ROLE [FLAGS]
```

The command outputs nothing and exits with `0` on success. If there was an error, the error will be
printed and the command will terminate with a non-zero exit code.
