import com.lightbend.lagom.core.LagomVersion

organization in ThisBuild := "com.example"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.12.8"

// Update the version generated by sbt-dynver to remove any + characters, since these are illegal in docker tags
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))

val hibernateEntityManager = "org.hibernate" % "hibernate-entitymanager" % "5.4.2.Final"
val jpaApi = "org.hibernate.javax.persistence" % "hibernate-jpa-2.1-api" % "1.0.0.Final"
val validationApi = "javax.validation" % "validation-api" % "1.1.0.Final"

val akkaVersion = "2.6.0-M3"

lazy val `shopping-cart-java` = (project in file("."))
  .aggregate(`shopping-cart-api`, `shopping-cart`)

lazy val `shopping-cart-api` = (project in file("shopping-cart-api"))
  .settings(common)
  .settings(libraryDependencies ++= Seq(lagomJavadslApi, lombok))

lazy val `shopping-cart` = (project in file("shopping-cart"))
  .enablePlugins(LagomJava)
  .settings(common)
  .settings(dockerSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
      // Netty needed for Akka classic remoting
      "io.netty" % "netty" % "3.10.6.Final",
      lagomJavadslPersistenceJdbc,
      lagomJavadslPersistenceJpa,
      lagomLogback,
      lagomJavadslTestKit,
      lombok,
      postgresDriver,
      hamcrestLibrary,
      lagomJavadslAkkaDiscovery,
      akkaDiscoveryKubernetesApi,
      hibernateEntityManager,
      jpaApi,
      validationApi
    )
  )
  .settings(lagomForkedTestSettings: _*)
  .settings(lagomServiceHttpPort := 10003)
  .dependsOn(`shopping-cart-api`)

val lombok = "org.projectlombok" % "lombok" % "1.18.6"
val postgresDriver = "org.postgresql" % "postgresql" % "42.2.5"
val hamcrestLibrary = "org.hamcrest" % "hamcrest-library" % "2.1" % Test

val akkaManagementVersion = "1.0.0"
val akkaDiscoveryKubernetesApi = "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % akkaManagementVersion
val lagomJavadslAkkaDiscovery = "com.lightbend.lagom" %% "lagom-javadsl-akka-discovery-service-locator" % LagomVersion.current

def common = Seq(
  javacOptions in (Compile, compile) ++= Seq(
    "-Xlint:unchecked",
    "-Xlint:deprecation",
    "-parameters",
    "-Werror"
  )
)

def dockerSettings = Seq(
  dockerUpdateLatest := true,
  dockerBaseImage := "adoptopenjdk/openjdk8",
  dockerUsername := sys.props.get("docker.username"),
  dockerRepository := sys.props.get("docker.registry")
)

lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false
lagomServiceGatewayPort in ThisBuild := 10001
lagomServiceLocatorPort in ThisBuild := 10002
