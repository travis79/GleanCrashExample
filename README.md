# Recording Android Crashes with Glean

For the last year I’ve been working on this shiny new library by Mozilla known as [Glean SDK](https://github.com/mozilla/glean). The Glean SDK is a cross-platform telemetry library that makes collecting data easier for developers to understand and instrument in their applications.  Just take a look to the tagline, “Telemetry for humans” to understand the objective of Glean.  Glean is being used right now in things like [Firefox Preview](https://github.com/mozilla-mobile/fenix/) and [Firefox for Fire TV](https://github.com/mozilla-mobile/firefox-tv/) and I’m sure we will see it in even more Mozilla products very soon.

One of the things that we might want to collect some data on in the applications we create is crashes.  Every application will have them from time to time.  Sometimes they are the application’s fault, sometimes it’s the fault of the OS, but they happen and we usually want to know more about them so we can fix them (if possible).  So let’s take a look at how we can use Glean to instrument an Android application and record some data when it crashes.

## Before We Start

There are a few things that we need to have installed in order to get ready.  The biggest one of those is [Android Studio](https://developer.android.com/studio/), which (if you include the Android SDK) can take a little while to download and get installed.  Second, I’m going to recommend having the [Book of Glean](https://mozilla.github.io/glean/book/) open and available as I will be referring to it throughout this walk-through as it contains all the Glean documentation we need to instrument our app.  I’m also assuming that you have some knowledge of Android application development, we won’t be doing anything too involved, but knowing where to go to create a new project, and how to add dependencies to a Gradle file will be helpful.

## Create A Project

With our tools and documentation installed and open, let’s start by creating a new Android Studio project.  It could be any type of project to start with, but I’m choosing the “Basic Activity” template simply because it comes with a button already on the screen that can use to crash our app in order to collect our metrics.  Give the project a name of "GleanCrashExample", choose Kotlin for the language, and choose API level 21 for the minimum SDK level.

## Setup Build Configuration

### Add Glean As A Dependency

Now that we have a project started, we can add Glean to it as a dependency in the Gradle configuration.  Find the `build.gradle` file for your _app module_ and add the following line to the dependencies section:

```Groovy
implementation 'org.mozilla.components:service-glean:16.0.0'
```

As of this writing, version `16.0.0` is the latest version of [Android-Components](https://github.com/mozilla-mobile/android-components/).  Android-Components is a collection of tools for building browsers on Android and it is the easiest way to consume Glean on Android at the moment.  You should probably take a look at the Android-Components releases and check that you are using the latest version of Android-Components for your project.  So just replace the version above with the latest.

### Add Python Environment

We add a Python environment for the [glean_parser](https://github.com/mozilla/glean_parser/) to run in with the following lines, added to the very top of the `build.gradle` file:

```Groovy
plugins {
    id "com.jetbrains.python.envs" version "0.0.26"
}
```

### Import SDK Generator Script

The last part of the changes to the app’s build.gradle file is to include the `sdk_generator.gradle` script.  Right before the end of the `build.gradle` file, add the following (making sure the version here matches the version of the `implementation` line above):

```Groovy
apply from: 'https://github.com/mozilla-mobile/android-components/raw/v16.0.0/components/service/glean/scripts/sdk_generator.gradle'
```

### Add Mozilla Maven Repository

One final step is necessary in order to be able to download Glean from Mozilla’s maven repository.  We need to add the repo URL to the Project build.gradle file.  In Android Studio, for our project named “GleanCrashExample”, there will be a build.gradle that has `(Project: GleanCrashExample)` next to it.  In this Gradle file, you will find a section called `allProjects` which has in it a section called `repositories`.  You may already find `google()` and `jcenter()` there, but we need to add the following in order to download Glean:

```Groovy
maven {
    url "https://maven.mozilla.org/maven2"
}
```

That should be it for additions to the build configuration files.  You should try to do a Gradle sync now to make sure everything works.  If you have any problems, try going back through the configuration steps to make sure you got everything.

## Instrument The Project With Glean

### Initial Integration

Now that we have added Glean to our app, we need to do a little initialization in order to get it ready to record and send data. At the top of your `MainActivity.kt` file, add the following import statement:

```Kotlin
import mozilla.components.service.glean.Glean
```

Then inside of the `onCreate()` function of the `MainActivity`, add the following 2 lines to enable and initialize Glean:

```Kotlin
Glean.setUploadEnabled(true)
Glean.initialize(this)
```

That’s it!  The app now has Glean installed and enabled and will be able to send the baseline ping without any additional work!

### Add Custom Metric

Since we are instrumenting crashes with some custom metrics, we still have some work to do.  We will need to add a `metrics.yaml` file to define the metrics we will use to record our crash information and a `pings.yaml` file to define a custom ping which will give us some control over the scheduling of the uploading of the crash telemetry.

In order to do this we need to make a decision.  Mainly, what metric type will we use to represent our crash data.  This could probably be argued several ways, but I have chosen to instrument it as an [event](https://mozilla.github.io/glean/book/user/metrics/event.html), simply because events capture information in a nice concise way and have a built-in way of passing additional information using the `extras` field.  So if we want to pass along the cause, as well as a few lines of description or even potentially a few lines of stack trace, events let us do that easily (with [some limitations](https://mozilla.github.io/glean/book/user/metrics/event.html#limits)).

Now that we have decided what metric type we will use, we can now create our `metrics.yaml`.  Inside of the `app` folder in the Android Studio project we are going to create a new file and call it `metrics.yaml`.  Then we can add the schema definition and the metric definition to the file, so that it looks like this:

```YAML
# Required to indicate this is a `metrics.yaml` file
$schema: moz://mozilla.org/schemas/glean/metrics/1-0-0

crash:
  exception:
    type: event
    description: |
      Event to record crashes caused by unhandled exceptions
    notification_emails:
      - crashes@example.com
    bugs:
      - https://bugzilla.mozilla.org/show_bug.cgi?id=1582479
    data_reviews:
      - https://bugzilla.mozilla.org/show_bug.cgi?id=1582479
    expires:
      2099-01-01
    send_in_pings:
      - crash
    extra_keys:
      cause:
        description: The cause of the crash
      message:
        description: The exception message
```

By way of explanation, this creates a metric called `exception` within a metric category called `crash`.  There is a brief description and the required notification, bug, data review, and expiry fields.  Then we have the `send_in_pings` field with a value of `- crash`.  This means that we will send the crash data via a custom ping named `crash` (which we haven’t created, yet).  Finally we have the `extra_keys` field with two keys defined, `cause` and `message`.  This will allow us to send a couple of pieces of additional information along with the event.

### Add Custom Ping

Now we need to define the custom ping by creating a pings.yaml file in the same directory as we just created the `metrics.yaml` file.  We already know what the name of the ping is, `crash`, so the `pings.yaml` file should look like this:

```YAML
# Required to indicate this is a `pings.yaml` file
$schema: moz://mozilla.org/schemas/glean/pings/1-0-0

crash:
  description: >
    A ping to transport crash data
  include_client_id: true
  notification_emails:
    - crash@example.com
  bugs:
    - https://bugzilla.mozilla.org/show_bug.cgi?id=1582479
  data_reviews:
    - https://bugzilla.mozilla.org/show_bug.cgi?id=1582479
```

Before we can use our newly defined metric or ping, we need to build the application.  This will cause the [glean_parser](https://github.com/mozilla/glean_parser/) to work its magic and generate the API files that represent the metrics we defined.

**NOTE:** Just a little advice from my personal experience here, but I would recommend doing a Clean on your project before building _any_ time you have modified one of the Glean YAML files.  Sometimes it doesn't regenerate the files without the Clean so just something to keep in mind if you aren’t seeing your changes to the YAML files show up in the project.

We now have a couple of tasks to perform back in the MainActivity.kt file in order to make use of the metric and ping we defined.  First, we need to add an import line:

```Kotlin
import org.mozilla.gleancrashexample.GleanMetrics.Pings
```

And then we need to register our custom ping, so right after `Glean.initialize(this)` add the following line:

```Kotlin
Glean.registerPings(Pings)
```

This registers the custom ping with Glean so that it knows about it and can manage the storage and other important details of it like sending it when we tell it to.

### Instrument The App To Record The Event

Next, we need to make the `MainActivity` handle uncaught exceptions, so we extend the class definition by adding `Thread.UncaughtExceptionHandler` as an inherited class like this:

```Kotlin
class MainActivity : AppCompatActivity(), Thread.UncaughtExceptionHandler {
    ...
}
```

In order to be a responsible `Thread.UncaughtExceptionHandler`, we need to implement an override for the `uncaughtException()` function.  Somewhere in your `MainActivity` class, add the following override:

```Kotlin
override fun uncaughtException(p0: Thread, p1: Throwable) {
    Crash.exception.record(
        mapOf(
            Crash.exceptionKeys.cause to p1.cause!!.toString(),
            Crash.exceptionKeys.message to p1.message!!)
    )
    Pings.crash.send()
}
```

To explain what’s happening here, first we are recording to the `Crash.exception` metric we created.  If you recall, the category of the metric was `crash` and the name was `exception` so we access it by calling `record()` on the `Crash.exception` object that was created by the magic of Glean.  You can also see where we are passing in the extra information for the cause and the message which will get packaged up and sent along with our ping when the second action of `Pings.crash.send()` is called.  Basically this forces our ping to be sent immediately after the recording of the event.

Finally, we need to register our `MainActivity` as the default uncaught exception handler by adding the following line to the `onCreate()` function, after `Glean.initialize(this)`:

```Kotlin
Thread.setDefaultUncaughtExceptionHandler(this)
```

### Make A Way To Crash The App

That's pretty much it!  Now we just need to make a way to crash our app with an uncaught exception.  The "Basic Activity" template has a floating action button called `fab` that can be used, and it's set up in the `onCreate()` of the `MainActivity`.  We can change the click listener to make it look like this in order to make it throw an exception:

```Kotlin
fab.setOnClickListener {
    // Go boom!
    throw NullPointerException()
}
```

Now run the application and click the floating action button and you should have just crashed the app while recording and sending crash telemetry!  I should mention, this information didn't really get recorded by anything, as there is additional work that is required on our ingestion pipeline in order to accept telemetry from new applications, and that step hasn't been done, yet...

## Conclusion

If you are interested in seeing the fully operational project used to help write this tutorial, you can find it on GitHub [here](https://github.com/travis79/GleanCrashExample).
