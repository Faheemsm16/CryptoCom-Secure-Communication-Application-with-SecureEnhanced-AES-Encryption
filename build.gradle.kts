// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.3.2" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}
buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.3.15") //4.3.15
        classpath("com.android.tools.build:gradle:8.3.2")
         classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.9")
    }
}