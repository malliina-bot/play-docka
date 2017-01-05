import java.nio.charset.StandardCharsets

import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.MappingsHelper
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import com.typesafe.sbt.packager.docker.{Cmd, CmdLike, DockerPlugin, ExecCmd}
import com.typesafe.sbt.packager.universal.Archives
import org.apache.commons.io.FilenameUtils
import sbt.Keys._
import sbt._

/** Use `createBuildSpec` to write a buildspec.yml suitable for AWS CodeBuild and
  * the Elastic Beanstalk CLI.
  *
  * Task `ebDeploy` will make CodeBuild fetch the sources from git, package the app
  * and package the app after which it'll be deployed to Beanstalk.
  */
object BeanstalkPlugin {
  // Docker keys
  val dockerBaseDir = settingKey[String]("WORKDIR")
  val dockerExecutable = settingKey[String]("Docker executable script")
  val dockerZip = taskKey[File]("Zips the app")
  val dockerZipTarget = taskKey[File]("The target zip file")
  val dockerArtifacts = taskKey[Seq[File]]("Build output artifacts for buildspec.yml")
  // CodeBuild keys
  val buildSpecFile = taskKey[File]("The buildspec.yml file for AWS CodeBuild")
  val tempSpecFile = settingKey[File]("Temporary buildspec-temp.yml")
  val createBuildSpec = taskKey[File]("Builds and returns the AWS CodeBuild buildspec.yaml file")
  val codeBuild = taskKey[Unit]("Prepare for CodePipeline deployment")
  val codeBuildServiceRole = settingKey[String]("CodeBuild service role")
  val codeBuildArtifacts = taskKey[Seq[String]]("buildspec.yml entries under array files")
  // Beanstalk keys
  val ebConfigFile = taskKey[File]("Beanstalk config file")
  val tempEbConfigFile = taskKey[File]("Temporary beanstalk config file")
  val ebLabel = taskKey[String]("Beanstalk app version")
  val ebDeploy = taskKey[Unit]("Deploys the app to Elastic Beanstalk")
  val localEbDeploy = taskKey[Unit]("Deploys a locally packaged .zip to Beanstalk")

  // We roll the dockerCommands from scratch because we want to at least make the script executable
  def dockerSettings = Seq(
    dockerBaseDir := (defaultLinuxInstallLocation in Docker).value,
    dockerExecutable := s"${dockerBaseDir.value}/${dockerEntrypoint.value}",
    dockerExposedPorts := Seq(9000),
    dockerCommands := {
      val user = (daemonUser in Docker).value
      val group = (daemonGroup in Docker).value
      Seq(
        Cmd("FROM", dockerBaseImage.value),
        Cmd("WORKDIR", dockerBaseDir.value),
        makeAdd(dockerBaseDir.value),
        makeChown(user, group, Seq(".")),
        ExecCmd("RUN", "chmod" :: "u+x" :: dockerEntrypoint.value.toList: _*),
        Cmd("EXPOSE", dockerExposedPorts.value.mkString(" ")),
        Cmd("USER", user),
        ExecCmd("ENTRYPOINT", dockerEntrypoint.value: _*),
        ExecCmd("CMD", dockerCmd.value: _*)
      )
    },
    dockerZipTarget := (target in Docker).value / s"${ebLabel.value}.zip",
    dockerZip := {
      val dest = zipDir(
        (stagingDirectory in Docker).value,
        dockerZipTarget.value
      )
      streams.value.log.info(s"Created $dest")
      dest
    },
    dockerZip := (dockerZip dependsOn (stage in Docker)).value,
    buildSpecFile := baseDirectory.value / BuildSpec.FileName,
    tempSpecFile := baseDirectory.value / "buildspec-temp.yml",
    createBuildSpec := {
      val dest = buildSpecFile.value
      val artifacts = codeBuildArtifacts.value
      val buildSpecContents = BuildSpec.writeForArtifact(codeBuild.key.label, codeBuildServiceRole.value, artifacts, dest)
      streams.value.log.info(s"$buildSpecContents")
      dest
    },
    codeBuild := {
      val baseDir = baseDirectory.value
      val stagingDir = (stagingDirectory in Docker).value
      val destFiles = dockerArtifacts.value.map(a => baseDir / a.relativeTo(stagingDir).get.toString)
      failIfExists(destFiles)
      IO.copyDirectory(stagingDir, baseDir)
    },
    codeBuild := (codeBuild dependsOn (stage in Docker)).value,
    dockerArtifacts := (stagingDirectory in Docker).value.listFiles(),
    dockerArtifacts := (dockerArtifacts dependsOn (stage in Docker)).value,
    codeBuildArtifacts := {
      def unixify(s: File) = s.toString.replace('\\', '/')

      // Prefers failure over flatMap
      dockerArtifacts.value map { p =>
        val baseRelative = unixify(p.relativeTo((stagingDirectory in Docker).value).get)
        if (p.isDirectory) s"$baseRelative/**/*"
        else baseRelative
      }
    },
    codeBuildArtifacts := (codeBuildArtifacts dependsOn (stage in Docker)).value,
    ebConfigFile := baseDirectory.value / ".elasticbeanstalk" / "config.yml",
    tempEbConfigFile := baseDirectory.value / ".elasticbeanstalk" / "config-temp.yml",
    ebLabel := s"${name.value}-${version.value.replace('.', '-')}",
    ebDeploy := {
      val log = streams.value.log
      val cmd = Seq("eb", "deploy", "-l", ebLabel.value)
      log info s"Running '${cmd mkString " "}'... this make take a few minutes..."
      val exitValue = Process(cmd).run(log).exitValue()
      if (exitValue != 0) {
        sys.error(s"Unexpected exit value: $exitValue")
      }
    },
    localEbDeploy := deployLocalZip.value
  )

  def deployLocalZip = Def.taskDyn {
    deployLocally.dependsOn(renameSpecFile).doFinally(renameSpecFileBack.taskValue)
  }

  def renameSpecFile = Def.task {
    maybeRename(buildSpecFile.value, tempSpecFile.value)
  }

  def renameSpecFileBack = Def.task {
    maybeRename(tempSpecFile.value, buildSpecFile.value)
    ()
  }

  def maybeRename(file: File, dest: File) =
    if (file.exists()) file renameTo dest
    else false

  def deployLocally = Def.taskDyn {
    ebDeploy.dependsOn(changeEbConf).doFinally(changeEbConfBack.taskValue)
  }

  def changeEbConf = Def.task {
    val zip = dockerZip.value
    val ebConfig = ebConfigFile.value
    if (!ebConfig.exists()) sys.error(s"$ebConfig does not exist")
    val oldConfig = IO.read(ebConfig, StandardCharsets.UTF_8)
    val tempConf = tempEbConfigFile.value
    val renameSuccess = maybeRename(ebConfig, tempConf)
    if (!renameSuccess) sys.error(s"Unable to rename $ebConfig to $tempConf")
    val deploySection = Yaml.section("deploy")(
      Yaml.single("artifact", (zip relativeTo baseDirectory.value).get)
    )
    val newConfig = s"$oldConfig\n${Yaml.stringify(deploySection)}"
    streams.value.log.info(s"Writing $ebConfig")
    IO.write(ebConfig, newConfig, StandardCharsets.UTF_8, append = false)
  }

  def changeEbConfBack = Def.task {
    val tempConf = tempEbConfigFile.value
    val ebConf = ebConfigFile.value
    if (tempConf.exists() && ebConf.exists()) ebConf.delete()
    maybeRename(tempConf, ebConf)
    ()
  }

  def failIfExists(files: Seq[File]) = files foreach { file =>
    if (file.exists()) {
      val desc = if (file.isDirectory) "Directory" else "File"
      sys.error(s"$desc must not exist: $file")
    }
  }

  /** Zips `sourceDir`.
    *
    * @param sourceDir directory to zip
    * @param zipTarget desired zip file
    * @return the zipped file
    */
  def zipDir(sourceDir: File, zipTarget: File): File = {
    val targetDir = Option(zipTarget.getParentFile)
      .getOrElse(sys.error(s"Target must have a parent: $zipTarget"))
    val name = FilenameUtils.getBaseName(zipTarget.getName)
    val mappings = MappingsHelper.contentOf(sourceDir)
    Archives.makeZip(targetDir, name, mappings, None)
  }

  private final def makeAdd(dockerBaseDirectory: String): CmdLike = {
    val files = dockerBaseDirectory.split(DockerPlugin.UnixSeparatorChar)(1)
    Cmd("ADD", s"$files /$files")
  }

  private final def makeChown(daemonUser: String, daemonGroup: String, directories: Seq[String]): CmdLike =
    ExecCmd("RUN", Seq("chown", "-R", s"$daemonUser:$daemonGroup") ++ directories: _*)
}