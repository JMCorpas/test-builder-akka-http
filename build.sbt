
name := "test"
version := "0.1"
scalaVersion := "2.13.1"
lazy val akkaHttpVersion = "10.1.11"
lazy val akkaVersion    = "2.6.4"
lazy val dockerImageTag = "test:0.1"
lazy val builderJdkDockerFile = "azul/zulu-openjdk-alpine:11"
lazy val DockerFile = "deployment/Dockerfile"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"                % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
      "ch.qos.logback"    % "logback-classic"           % "1.2.3",

      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                % "3.0.8"         % Test
    )
  )
  .settings(
    mainClass in assembly := Some("com.Main"),
    assemblyJarName in assembly := name.value + version.value + ".jar"

  )

val build = taskKey[Unit](
  "Create docker image with custom Zulu JVM"
)

build := {

  import java.nio.file.{Files, StandardCopyOption}
  import scala.reflect.io.Directory

  val logger = streams.value.log
  val sourceFatJar = (assemblyOutputPath in assembly).value
  val stageDir = target.value / "customJdk" / "stage"
  val tarjetFatJar = stageDir / sourceFatJar.name

  stageDir.mkdirs()
  Files.copy(sourceFatJar.toPath,tarjetFatJar.toPath,StandardCopyOption.REPLACE_EXISTING)

  var dependencies : List[String] = List()
  val jdepsCommand = Seq(
    "docker",
    "run",
    "--rm",
    "-v",
    s"${stageDir.getAbsolutePath}:/deps",
    builderJdkDockerFile,
    "/usr/lib/jvm/zulu11/bin/jdeps",
    "--list-deps",
    "/deps/" + sourceFatJar.name
  )

  def filterDeps (dep: String) : List[String] = {
    val depTrim = dep.replaceAll(" ", "")
    val pattern = """(.*)/""".r
    try {
      val result = pattern.findFirstMatchIn(depTrim).get.group(1)
      if (result.contains(".")) List(result) else List()
    }
    catch {
      case _: Throwable => List(depTrim)
    }
  }

  logger.info(s"Getting dependencies")
  logger.info(s"Running: ${jdepsCommand.mkString(" ")}")
  sys.process.Process(jdepsCommand) ! sys.process.ProcessLogger(dep => {
    println(dep)
    dependencies = dependencies ++ filterDeps(dep)
  }
  ) match {
    case 0 => logger.info(dependencies.toString)
    case _ => sys.error(s"Jdeps error: $dependencies")
  }
  dependencies = dependencies.distinct

  val jdkDir = Directory (stageDir / "jdk")
  jdkDir.deleteRecursively()

  var user : String = ""
  sys.process.Process(Seq ("bash", "-c", "echo $(id -u)")) ! sys.process.ProcessLogger(value => user=value)

  val runBuilderCustomJdk = Seq(
    "docker",
    "run",
    "--rm",
    "-u",
    user,
    "-v",
    s"${stageDir.getAbsolutePath}:/customjdk",
    builderJdkDockerFile,
    "/usr/lib/jvm/zulu11/bin/jlink",
    "--compress=1",
    "--strip-debug",
    "--no-header-files",
    "--no-man-pages",
    "--add-modules",
    dependencies.mkString(","),
    "--output",
    "/customjdk/jdk"
  )
  logger.info(s"Building custom jvm in directory ${stageDir.getAbsolutePath}")
  logger.info(s"Running: ${runBuilderCustomJdk.mkString(" ")}")
  sys.process.Process(runBuilderCustomJdk, None) ! streams.value.log match {
    case 0 => logger.info ("Successful")
    case r => sys.error(s"Failed to run docker, exit status: " + r)
  }
  val runBuilderFinalImage = Seq(
    "docker",
    "build",
    "--build-arg",
    "JDK=jdk",
    "--build-arg",
    "APP=" + sourceFatJar.name,
    "-t",
    dockerImageTag,
    "-f",
    (baseDirectory.value / DockerFile).getAbsolutePath,
    stageDir.absolutePath
  )

  logger.info(s"Building final docker image $dockerImageTag")
  logger.info(s"Running: ${runBuilderFinalImage.mkString(" ")}")
  sys.process.Process(runBuilderFinalImage, None) ! streams.value.log match {
    case 0 => logger.info ("Successful")
    case r => sys.error(s"Failed to run docker, exit status: " + r)
  }

}

build := ( build dependsOn assembly).value