// The Lagom plugin
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % sys.props.getOrElse("lagom.version", "1.5.1"))
// Set the version dynamically to the git hash
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "3.3.0")
// Not needed once upgraded to Play 2.7.1
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.19")
