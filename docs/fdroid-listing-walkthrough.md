# F-Droid listing walkthrough

This run does not publish a release.
The operator holds the signing key outside the repo, to keep the key out of git.

## Operator job

The operator builds a release later.
The operator confirms a reproducible build later.
The operator files the public listing later.

## Tester guide

Testers follow docs/fdroid-tester-tutorial.md.
This walkthrough does not repeat the install steps.

## Build facts

Gradle emits versionName 0.1.0 and versionCode 1.
CHANGELOG section 0.4.5 is ahead of those Gradle values.
Do not edit Gradle to close that gap.
The application id is `app.toebeans.android`.
The license is AGPL-3.0.
The public git URL is https://github.com/weijia-89/toebeans.

## Tester channel

The tester channel is not a completed install in this run.
The operator publishes the signed build later.
Use Obtainium from a GitHub release first.
Use a small F-Droid repo in Droid-ify second.
Droid-ify is a client.
Droid-ify reads a repository.
Droid-ify is not a store you submit to.

## Signing key

Hold the signing key outside the repo.
Do not generate a keystore in this run.
Do not commit a password or a token.
Do not add a Gradle signing block in this docs change.

## Submit later

Leave every box below unchecked.
This run does not file a store request.

- [ ] Build the release after the key is outside the repo.
- [ ] Confirm the APK versionName is 0.1.0.
- [ ] Confirm the APK versionCode is 1.
- [ ] Confirm a reproducible build before a public listing.
- [ ] File the fdroiddata merge request later.
- [ ] File an IzzyOnDroid request only if the main queue waits.

IzzyOnDroid is the faster public listing if the main F-Droid queue waits.
Do not file that request in this run.
Do not file the fdroiddata merge request in this run.

## Metadata draft

The metadata draft is docs/fdroid/app.toebeans.android.yml.
The draft copies the public git URL.
The draft copies license AGPL-3.0.
The draft copies application id `app.toebeans.android`.
The draft copies versionName 0.1.0 and versionCode 1 from Gradle.
The draft contains no password and no token.
The Gradle root is the repository root.
Do not set subdir to androidApp.
Replace commit REPLACE_WITH_RELEASE_TAG with the release tag.
This run does not cut that tag.

## Play file

Read the Milestone 1.2 line in docs/ROADMAP.md before you open the Play file.
The Play file stays in the repo.

## Cleanup

The LICENSE file is present.
The LICENSE header names the GNU Affero General Public License, version 3.
androidApp/build.gradle.kts lists AndroidX and Compose.
The same file lists Koin and kotlinx-datetime.
The file has no proprietary library.
The file has no tracking SDK.
Run the network fitness command from the repository root.

```bash
bash scripts/test_no_network.sh .
```

The smallest doc fix is the roadmap row in this change.
Do not clean unrelated code.

## Device session

This run records the device session as SKIPPED.
This run does not boot an emulator for acceptance.
