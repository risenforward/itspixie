unmanagedBase := baseDirectory.value / "libs"

addSbtPlugin("io.spray"          % "sbt-revolver"        % "0.9.1")
addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager" % "1.3.2")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker"          % "1.4.1")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"        % "0.14.6")
addSbtPlugin("io.get-coursier" % "sbt-coursier"       % "1.0.0-RC12")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
