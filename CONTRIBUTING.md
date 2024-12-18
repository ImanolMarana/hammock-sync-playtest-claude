# Contributing

## Pull requests

We welcome pull requests.

## General information

Hammock Sync for Android is written in Java and uses [gradle](http://www.gradle.org) 
as its build tool.

### Coding guidelines

Adopting the [Google Java Style](https://google-styleguide.googlecode.com/svn/trunk/javaguide.html)
with the following changes:

```
4.2
    Our block indent is +4 characters

4.4
    Our line length is 100 characters.

4.5.2
    Indent continuation of +4 characters fine, but I think
    IDEA defaults to 8, which is okay too.
```

## Requirements

The main requirements are:

* Java 17
* Android SDK

Optionally, and recommended:

* CouchDB

### Installing requirements

#### Java

Follow the instructions for your platform.

#### CouchDB

Again, using brew:

```bash
$ brew install couchdb
```

#### Android SDK

Follow the instructions provided on the android developer site.

## Building

The project should build out of the box with:

```bash
$ ./gradlew build
```

Note: for windows machines the script to run is `gradlew.bat`.

This will download the dependencies, build the library and run the unit tests.

If you want to use the library in other projects, install it to your local maven repository:

```bash
$ gradlew publishToMavenLocal
```

## Testing

### Running integration tests

These require a running couchdb.

```bash
$ ./gradlew integrationTest
```

#### Running integration tests on Android

Running the integration tests on Android requires running the tests from an
Android context. 

The test application build scripts require a running emulator and the ```ANDROID_HOME```
environment variable to be set

The minimum requirements for an Android emulator:

* Minimum API Level 29 (Target API Level is 33)
* An SD card

The tests can be run like any other Android application test suite, through
Android Studio / IntelliJ Idea or via the command line.

Steps to run the tests:

1. Start Android emulator
2. Run the following command from the root of the repo

```bash
$ ../gradlew clean connectedCheck
```

#### Collecting Test Results

Test Results are collected in the [usual manner for Android tests](https://developer.android.com/tools/testing/index.html).
If run via Android Studio / IntelliJ IDEA the results will appear in the UI.
If you run via the command line the test report will appear in the `build/reports/`
directory of the app module.

#### Testing using remote CouchDB Instance

Certain tests need a running CouchDB instance, by default they use the
local CouchDB.

To run tests with a remote CouchDB, you need set the details of this
CouchDB server, including access credentials. This can be done by
adding options to a file named `gradle.properties` in the same folder
as `build.gradle`, eg:

```
systemProp.test.couch.username=yourUsername
...
```

Consult the [list of test options](#test-options) for details of all
options available.

It is also possible to pass in these options from the gradle command
line by using the -D switch eg. `-Dtest.couch.username=yourUsername`

Your gradle.properties should NEVER be checked into git repo as it contains your CouchDB credentials.

## Using Android Studio
For Android Studio, this plugin is already installed.

You can then use File -> Import Project (Import Project or Import
Non-Android Studio project from the Welcome dialog) to create the IDEA
/ Android Studio project, which will allow you to run gradle tasks
from IDEA / Android Studio in addition to setting up dependencies
correctly. Just select the `build.gradle` file from the root of the
project directory in the Import dialog box and select the default
settings.

After importing the gradle project you may need to configure the
correct SDK. This can be set from the File -> Project Structure
dialog. In the Project Settings -> Project tab. If the selected entry
in the Project SDK dropdown is `<No SDK>`, then change it to an
appropriate Java SDK such as Java 17.

### Code Style

An IDEA code style matching the guidelines above is included in the project,
in the `.idea` folder.

If you already have the project, enable the code style follow these steps:

1. Go to _Preferences_ -> _Editor_ -> _Code Style_.
2. In the _Scheme_ dropdown, select _Project_.

IDEA will then use the style when reformatting, refactoring and so on.


### Running JUnit tests in the IDE

To run JUnit tests from Android Studio / IDEA, you will need to add some configuration to tell it
where the SQLite native library lives.

* In the menu, select Run -> Edit Configurations
* In the left hand pane, select Defaults -> JUnit
* Set VM options to:
```
-ea -Dsqlite4java.library.path=native
```

## Test Options

### Options which describe how to access the remote CouchDB/Cloudant server

`test.couch.uri`

If specified, tests will run against an instance described by this
URI.

This will over-ride any of the other `test.couch` options given.  This
URI can include all of the components necessary including username and
password for basic auth.

It is not necessary to set `test.with.specified.couch` to true in
order to use this option.

`test.with.specified.couch`

If true, tests will run against an instance described by
`test.couch.username`, `test.couch.password`, `test.couch.host`,
`test.couch.port`, `test.couch.http` with default values supplied as
appropriate.

If false, tests will run against a local couch instance on the default
port over http.

`test.couch.username`

If specified, tests will use this username to access the instance with
basic auth.

Alternatively, if `test.couch.use.cookies` is true, tests will use
this username to refresh cookies.

`test.couch.password`

If specified, tests will use this password to access the instance with
basic auth.

Alternatively, if `test.couch.use.cookies` is true, tests will use
this password to refresh cookies.

`test.couch.host`

If specified, tests will use this hostname or IP address to access the
instance.

Otherwise, a default value of "localhost" is used.

`test.couch.port`

If specified, tests will use this port to access the instance.

Otherwise, a default value of 5984 is used.

`test.couch.http`

If specified, tests will use this protocol to access the
instance. Valid values are "http" and "https".

Otherwise, a default value of "http" is used.

`test.couch.use.cookies`

If true, use cookie authentication (via the HTTP interceptors) instead
of basic authentication to access the instance.

### Options which control whether we should ignore certain tests

`test.couch.ignore.compaction`

If true, do not run tests which expect a /_compact endpoint to
exist. The Cloudant service does not expose this endpoint as
compaction is performed automatically.

`test.couch.ignore.auth.headers`

If true, do not run tests like testCookieAuthWithoutRetry.
