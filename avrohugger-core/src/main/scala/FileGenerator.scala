package avrohugger

import avrohugger.format.abstractions.SourceFormat
import avrohugger.input.DependencyInspector
import avrohugger.input.NestedSchemaExtractor
import avrohugger.input.reflectivecompilation.schemagen._
import avrohugger.input.parsers.{ FileInputParser, StringInputParser}
import avrohugger.matchers.TypeMatcher
import avrohugger.stores.{ ClassStore, SchemaStore }

import java.io.{File, FileNotFoundException, IOException}

import org.apache.avro.{ Protocol, Schema }
import org.apache.avro.Schema.Type.ENUM

// Unable to overload this class' methods because outDir uses a default value
private[avrohugger] object FileGenerator {

  def schemaToFile(
    schema: Schema,
    outDir: String,
    format: SourceFormat,
    classStore: ClassStore,
    schemaStore: SchemaStore,
    typeMatcher: TypeMatcher,
    restrictedFields: Boolean): Unit = {
    val topNS: Option[String] = DependencyInspector.getReferredNamespace(schema)
    val topLevelSchemas: List[Schema] =
      NestedSchemaExtractor.getNestedSchemas(schema, schemaStore)
    // most-nested classes processed first
    topLevelSchemas.reverse.distinct.foreach(schema => {
      // pass in the top-level schema's namespace if the nested schema has none
      val ns = DependencyInspector.getReferredNamespace(schema) orElse topNS
      format.compile(classStore, ns, Left(schema), outDir, schemaStore, typeMatcher, restrictedFields)
    })
  }

  def protocolToFile(
    protocol: Protocol,
    outDir: String,
    format: SourceFormat,
    classStore: ClassStore,
    schemaStore: SchemaStore,
    typeMatcher: TypeMatcher,
    restrictedFields: Boolean): Unit = {
    val ns = Option(protocol.getNamespace)
    format.compile(classStore, ns, Right(protocol), outDir, schemaStore, typeMatcher, restrictedFields)
  }

  def stringToFile(
    str: String,
    outDir: String,
    format: SourceFormat,
    classStore: ClassStore,
    schemaStore: SchemaStore,
    stringParser: StringInputParser,
    typeMatcher: TypeMatcher,
    restrictedFields: Boolean): Unit = {
    val schemaOrProtocols = stringParser.getSchemaOrProtocols(str, schemaStore)
    schemaOrProtocols.foreach(schemaOrProtocol => {
      schemaOrProtocol match {
        case Left(schema) => {
          schemaToFile(schema, outDir, format, classStore, schemaStore, typeMatcher, restrictedFields)
        }
        case Right(protocol) => {
          protocolToFile(protocol, outDir, format, classStore, schemaStore, typeMatcher, restrictedFields)
        }
      }
    })
  }

  def fileToFile(
    inFile: File,
    outDir: String,
    format: SourceFormat,
    classStore: ClassStore,
    schemaStore: SchemaStore,
    fileParser: FileInputParser,
    typeMatcher: TypeMatcher,
    restrictedFields: Boolean): Unit = {
    val schemaOrProtocols: List[Either[Schema, Protocol]] =
      fileParser.getSchemaOrProtocols(inFile, format, classStore)
    schemaOrProtocols.foreach(schemaOrProtocol => schemaOrProtocol match {
      case Left(schema) => {
        schemaToFile(schema, outDir, format, classStore, schemaStore, typeMatcher, restrictedFields)
      }
      case Right(protocol) => {
        protocolToFile(protocol, outDir, format, classStore, schemaStore, typeMatcher, restrictedFields)
      }
    })
  }

}
