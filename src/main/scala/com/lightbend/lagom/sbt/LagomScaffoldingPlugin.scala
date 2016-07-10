package com.lightbend.lagom.sbt

import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser


/**
  * Created by fsznajderman on 29/06/16.
  */
object LagomScaffoldingPlugin extends AutoPlugin {

  //definition of parser
  val cmdParser: Parser[(String, Option[String])] =
    (Space ~> (StringBasic ~ (Space ~> "org:" ~> StringBasic).?)) !!! ("Command should looks like : newService <name of service> [org:name of organisation]")


  val newJavaService = inputKey[Unit]("Create new Lagom service based on Java")
  val newScalaService = inputKey[Unit]("Create new Lagom service based on Scala")

  override def projectSettings = {
    Seq(newJavaService := {
      val log = streams.value.log

      buildService(cmdParser.parsed)

      def buildService(serviceInfo: (String, Option[String])): Unit = {
        val serviceName = serviceInfo._1
        val packName = serviceInfo._2.getOrElse(Option(organization.value).getOrElse(""))
        if (packName.isEmpty) log.warn("No package has been defined")
        val sourceDir = "src/main/java"
        val resourceDir = "src/main/resources"
        log.info(managePack(packName))
        val apiDir = baseDirectory.value / ((serviceName + "-api") + "/" + sourceDir + "/" + managePack(packName))
        val implDir = baseDirectory.value / ((serviceName + "-impl") + "/" + sourceDir + "/" + managePack(packName))
        val confDir = baseDirectory.value / ((serviceName + "-impl") + "/" + resourceDir)

        //create directories
        IO.createDirectories(List(apiDir, implDir))
        createInterfaceFile(packName, serviceName, apiDir)
        createImplFiles(packName, serviceName.capitalize, implDir)
        createConfFile(packName, serviceName, confDir)
        addServiceConfToBuild(serviceName, baseDirectory.value)
        log.info("Lagom(Java) has been generated successfuly")
      }

      def managePack(packName: String): String = packName.replace(".", "/")

      def createInterfaceFile(packName: String, name: String, dir: File): Unit = {

        val interface =
          s"""
             |/*
             | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             | */
             |package $packName ;
             |
             |import com.lightbend.lagom.javadsl.api.Descriptor;
             |import com.lightbend.lagom.javadsl.api.Service;
             |import static com.lightbend.lagom.javadsl.api.Service.*;
             |
             |public interface ${name.capitalize}Service extends Service {
             |
             |    @Override
             |    default Descriptor descriptor() {
             |        return named("$name");
             |    }
             |}
        """.stripMargin

        IO.append(dir / (s"${name.capitalize}Service.java"), interface)
      }

      def createImplFiles(packName: String, name: String, dir: File): Unit = {
        val implementation =
          s"""/*
              | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
              | */
              |package $packName;
              |
            |import $packName.${name}Service;
              |
           |
           |/**
              | * Implementation of the ${name}Service.
              | */
              |public class ${name}ServiceImpl implements ${name}Service {
              |
           |    //TODO implement service interface
              |
           |}
              |""".stripMargin

        val module =
          s"""package $packName;
              |
              |import com.google.inject.AbstractModule;
              |import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
              |import $packName.${name}Service;
              |import $packName.${name}ServiceImpl;
              |
            |/**
              | * The module that binds the ${name}ServiceModule so that it can be served.
              | */
              |public class ${name}ServiceModule extends AbstractModule implements ServiceGuiceSupport {
              |  @Override
              |  protected void configure() {
              |    bindServices(serviceBinding(${name}Service.class, ${name}ServiceImpl.class));
              |  }
              |}""".stripMargin

        IO.append(dir / (s"${name}ServiceImpl.java"), implementation)
        IO.append(dir / (s"${name}ServiceModule.java"), module)

      }
      def createConfFile(packName: String, name: String, dir: File): Unit = {

        val applicationConf =
          s"""#
              |# Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
              |#
              |play.modules.enabled += $packName.${name.capitalize}ServiceModule
              |""".stripMargin

        IO.append(dir / ("application.conf"), applicationConf)
      }

      def addServiceConfToBuild(name: String, dir: File) = {
        val sbtConf =
          s"""
             |
             |//${name} service
             |lazy val ${name}Api = (project in file("$name-api"))
             |  .settings(
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies += lagomJavadslApi
             |  )
             |
              |lazy val ${name}Impl = (project in file("$name-impl"))
             |  .enablePlugins(LagomJava)
             |  .settings(
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies ++= Seq(
             |      lagomJavadslPersistence,
             |      lagomJavadslTestKit
             |    )
             |  )
             |  .settings(lagomForkedTestSettings: _*)
             |  .dependsOn("${name}Api")""".stripMargin

        IO.append(dir / ("build.sbt"), sbtConf)
      }

    }, newScalaService := {
      val log = streams.value.log

      buildService(cmdParser.parsed)

      def buildService(serviceInfo: (String, Option[String])): Unit = {
        val serviceName = serviceInfo._1
        val packName = serviceInfo._2.getOrElse(Option(organization.value).getOrElse(""))
        if (packName.isEmpty) log.warn("No package has been defined")
        val sourceDir = "src/main/scala"
        val resourceDir = "src/main/resources"
        log.info(managePack(packName))
        val apiDir = baseDirectory.value / ((serviceName + "-api") + "/" + sourceDir + "/" + managePack(packName))
        val implDir = baseDirectory.value / ((serviceName + "-impl") + "/" + sourceDir + "/" + managePack(packName))
        val confDir = baseDirectory.value / ((serviceName + "-impl") + "/" + resourceDir)

        //create directories
        IO.createDirectories(List(apiDir, implDir))
        createInterfaceFile(packName, serviceName, apiDir)
        createImplFiles(packName, serviceName.capitalize, implDir)
        createConfFile(packName, serviceName, confDir)
        createConverterFile(baseDirectory.value / ((serviceName + "-impl") + "/" + sourceDir+ "/"))
        addServiceConfToBuild(serviceName, baseDirectory.value)
        log.info("Lagom(Scala) has been generated successfuly")
      }

      def managePack(packName: String): String = packName.replace(".", "/")

      def createInterfaceFile(packName: String, name: String, dir: File): Unit = {

        val interface =
          s"""
             |/*
             | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
             | */
             |package $packName
             |
           |import akka.stream.javadsl.Source
             |
           |import akka.NotUsed
             |import com.lightbend.lagom.javadsl.api.Descriptor
             |import com.lightbend.lagom.javadsl.api.ScalaService._
             |import com.lightbend.lagom.javadsl.api.Service
             |import com.lightbend.lagom.javadsl.api.ServiceCall
             |
           |trait ${name.capitalize}Service extends Service {
             |
           |  override def descriptor(): Descriptor = {
             |    named("${name.capitalize}")
             |    }
             |}
        """.stripMargin

        IO.append(dir / (s"${name.capitalize}Service.scala"), interface)
      }

      def createImplFiles(packName: String, name: String, dir: File): Unit = {
        val implementation =
          s"""/*
              | * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
              | */
              |package $packName
              |
            |import javax.inject.Inject
              |
            |import com.lightbend.lagom.javadsl.api.ServiceCall
              |import akka.Done
              |import akka.NotUsed
              |
            |import scala.concurrent.{ExecutionContext, Future}
              |
            |class ${name}ServiceImpl @Inject()()(implicit ex: ExecutionContext) extends ${name}Service {
              |
            |  // Needed to convert some Scala types to Java
              |  import converter.ServiceCallConverter._
              |}
              |
            |""".stripMargin

        val module =
          s"""package $packName
              |
              |import com.google.inject.AbstractModule
              |import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport
              |
            |/**
              | * The module that binds the ${name}ServiceModule so that it can be served.
              | */
              |class ${name}ServiceModule extends AbstractModule with ServiceGuiceSupport {
              |override def configure(): Unit = bindServices(serviceBinding(classOf[${name}Service], classOf[${name}ServiceImpl]))
              |
            |}""".stripMargin

        IO.append(dir / (s"${name}ServiceImpl.scala"), implementation)
        IO.append(dir / (s"${name}ServiceModule.scala"), module)

      }
      def createConfFile(packName: String, name: String, dir: File): Unit = {

        val applicationConf =
          s"""#
              |# Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
              |#
              |play.modules.enabled += $packName.${name.capitalize}ServiceModule
              |""".stripMargin

        IO.append(dir / ("application.conf"), applicationConf)
      }

      def addServiceConfToBuild(name: String, dir: File) = {
        val sbtConf =
          s"""
             |
           |//${name} service
             |lazy val ${name}Api = (project in file("$name-api"))
             |  .settings(
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies += lagomJavadslApi
             |  )
             |
              |lazy val ${name}Impl = (project in file("$name-impl"))
             |  .enablePlugins(LagomJava)
             |  .settings(
             |    scalacOptions in Compile += "-Xexperimental", // this enables Scala lambdas to be passed as Java SAMs
             |    version := "1.0-SNAPSHOT",
             |    libraryDependencies ++= Seq(
             |      lagomJavadslPersistence,
             |      lagomJavadslTestKit
             |    )
             |  )
             |  .settings(lagomForkedTestSettings: _*)
             |  .dependsOn("${name}Api")""".stripMargin

        IO.append(dir / ("build.sbt"), sbtConf)
      }

      def createConverterFile(dir: File) = {
        val serviceCallConverter =
          s"""
             |
             |package converter
             |
             |import java.util.concurrent.CompletionStage
             |
             |import com.lightbend.lagom.javadsl.api.ServiceCall
             |
             |object ServiceCallConverter extends CompletionStageConverters {
             |  implicit def liftToServiceCall[Request, Response](f: Request => CompletionStage[Response]): ServiceCall[Request,Response] =
             |    new ServiceCall[Request,Response] {
             |      def invoke(request: Request): CompletionStage[Response] = f(request)
             |  }
             |}""".stripMargin

        IO.append(dir / "converter" / ("ServiceCallConverter.scala"), serviceCallConverter)

        val CompletionStageConverters =
          s"""
             |
             |package converter
             |
             |import java.util.concurrent.CompletionStage
             |
             |import scala.compat.java8.FutureConverters.CompletionStageOps
             |import scala.compat.java8.FutureConverters.FutureOps
             |import scala.concurrent.Future
             |
             |import akka.NotUsed
             |
             |trait CompletionStageConverters {
             |
             |  implicit def asCompletionStage[A](f: Future[A]): CompletionStage[A] = f.toJava
             |  implicit def asFuture[A](f: CompletionStage[A]): Future[A] = f.toScala
             |
             |  implicit def asUnusedCompletionStage(f: CompletionStage[_]): CompletionStage[NotUsed] = f.thenApply(_ => NotUsed)
             |}
             |
             |object CompletionStageConverters extends CompletionStageConverters""".stripMargin

        IO.append(dir / "converter" / ("CompletionStageConverters.scala"), CompletionStageConverters)
      }


    })

  }


}
