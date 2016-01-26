sbtVersion := "0.13.9"

name := "dreamcrammer-test"

import android.Keys._

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")
//scalaVersion := "2.11.7"
scalaVersion := "2.10.2"

scalacOptions in Compile += "-feature"

updateCheck in Android := {} // disable update check

proguardScala in Android := false

useProguard in Android := true

//proguardCache in Android ++= Seq("org.scaloid")

proguardOptions in Android ++= Seq(
  "-dontobfuscate",
  "-dontoptimize",
  "-keepattributes Signature",
  "-printseeds target/seeds.txt" //,
  //  "-printusage target/usage.txt" //,
  //  "-keep class scala.collection.SeqLike {  public protected *; 	}",
  //  "-dontwarn sun.misc.Unsafe",
  //  "-keep class sun.misc.Unsafe {*;}",
  //  "-libraryjars /usr/local/lib/scala/lib/scala-reflect.jar",
  //  "-keep class scala.android.package**",
  //  "-keep class * extends scala.android.app.Activity",
  //  "-keep class * extends scala.runtime.MethodCache {    public <methods>; }",
  //  "-dontwarn scala.collection.**" // required from Scala 2.11.4
)

//libraryDependencies += "org.scaloid" %% "scaloid" % "4.1"

run <<= run in Android

install <<= install in Android

buildToolsVersion := Some("21.0.0")

dexMulti in Android := true

dexMinimizeMain in Android := true

dexMainClasses in Android := Seq(
  "com/example/app/MultidexApplication.class",
  "android/support/multidex/BuildConfig.class",
  "android/support/multidex/MultiDex$V14.class",
  "android/support/multidex/MultiDex$V19.class",
  "android/support/multidex/MultiDex$V4.class",
  "android/support/multidex/MultiDex.class",
  "android/support/multidex/MultiDexApplication.class",
  "android/support/multidex/MultiDexExtractor$1.class",
  "android/support/multidex/MultiDexExtractor.class",
  "android/support/multidex/ZipUtil$CentralDirectory.class",
  "android/support/multidex/ZipUtil.class"
)
// Tests //////////////////////////////

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.10.2",
  "com.android.support" % "support-v4" % "20.0.+"
)

// without this, @Config throws an exception,
unmanagedClasspath in Test ++= (bootClasspath in Android).value

sourceGenerators in Compile <+= Def.task {
  val file = (sourceManaged in Compile).value / "version.scala"
  IO.write(file,
    s"""
       |package ru.wordmetrix.dreamcrammer
       |object Version {
       | val branch="${git.gitCurrentBranch.value}"
       | val commit="${git.gitHeadCommit.value getOrElse "Unknown"}"
       | val date="${System.currentTimeMillis()}"
       | val version=s"$${branch}-$${commit}-$${date}"
       |}
          """.stripMargin)
  Seq(file)
}




