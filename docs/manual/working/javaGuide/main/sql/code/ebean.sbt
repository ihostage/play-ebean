//#add-sbt-plugin
addSbtPlugin("com.typesafe.play" % "sbt-play-ebean" % "6.2.0-RC7")
//#add-sbt-plugin

//#enable-plugin
lazy val myProject = (project in file("."))
  .enablePlugins(PlayJava, PlayEbean)

//#enable-plugin

//#play-ebean-models
Compile / playEbeanModels := Seq("models.*")
//#play-ebean-models

//#play-ebean-debug
playEbeanDebugLevel := 4
//#play-ebean-debug

//#play-ebean-agent-args
playEbeanAgentArgs += ("detect" -> "false")
//#play-ebean-agent-args

//#play-ebean-test
inConfig(Test)(PlayEbean.scopedSettings)

Test / playEbeanModels := Seq("models.*")
//#play-ebean-test
